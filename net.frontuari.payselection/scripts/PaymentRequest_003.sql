CREATE OR REPLACE VIEW adempiere.ftu_rv_openpayment
AS SELECT t.ad_client_id,
    t.ad_org_id,
    t.documentno,
    t.record_id,
    t.c_bpartner_id,
    t.datedoc,
    t.dateacct,
    t.openamt,
    t.c_doctype_id,
    t.issotrx,
    t.requesttype,
    t.duedate,
    bp.name,
    t.prepaidamt,
    t.allocatedamt,
    t.grandtotal,
    t.docstatus,
    t.c_currency_id,
    t.c_conversiontype_id
   FROM ( SELECT rv_openitem.ad_client_id,
            rv_openitem.ad_org_id,
            rv_openitem.documentno,
            rv_openitem.c_invoice_id AS record_id,
            rv_openitem.c_bpartner_id,
            rv_openitem.dateinvoiced AS datedoc,
            rv_openitem.dateacct,
            paymentrequestopen('API'::character varying, rv_openitem.c_invoice_id, rv_openitem.c_invoicepayschedule_id) AS openamt,
            rv_openitem.c_doctype_id,
            rv_openitem.issotrx,
            'API'::text AS requesttype,
            rv_openitem.duedate,
            rv_openitem.grandtotal,
            COALESCE(pay.payamt, 0::numeric) AS prepaidamt,
            COALESCE(rv_openitem.paidamt, 0::numeric) AS allocatedamt,
            rv_openitem.docstatus,
            rv_openitem.c_currency_id,
            rv_openitem.c_conversiontype_id
           FROM rv_openitem
             LEFT JOIN ( SELECT c_payment.c_order_id,
                    sum(c_payment.payamt) AS payamt
                   FROM c_payment
                  WHERE (c_payment.docstatus = ANY (ARRAY['CO'::bpchar, 'CL'::bpchar])) AND c_payment.isallocated = 'N'::bpchar
                  GROUP BY c_payment.c_order_id) pay ON pay.c_order_id = rv_openitem.c_order_id
        UNION ALL
         SELECT ftu_order_v.ad_client_id,
            ftu_order_v.ad_org_id,
            ftu_order_v.documentno,
            ftu_order_v.c_order_id AS record_id,
            ftu_order_v.c_bpartner_id,
            ftu_order_v.dateordered AS datedoc,
            ftu_order_v.dateacct,
            paymentrequestopen('POO'::character varying, ftu_order_v.c_order_id, ftu_order_v.c_orderpayschedule_id) AS openamt,
            ftu_order_v.c_doctype_id,
            ftu_order_v.issotrx,
            'POO'::text AS requesttype,
            ftu_order_v.duedate,
            ftu_order_v.grandtotal,
            0::numeric AS prepaidamt,
            0::numeric AS allocatedamt,
            ftu_order_v.docstatus,
            ftu_order_v.c_currency_id,
            ftu_order_v.c_conversiontype_id
           FROM ftu_order_v
          WHERE ftu_order_v.docstatus = 'WP'::bpchar OR ftu_order_v.docstatus = 'CO'::bpchar AND (EXISTS ( SELECT 1
                   FROM c_doctype dt
                  WHERE ftu_order_v.c_doctype_id = dt.c_doctype_id AND (dt.docsubtypeso = 'SO'::bpchar OR dt.docbasetype = 'POO'::bpchar))) AND (EXISTS ( SELECT 1
                   FROM c_orderline ol
                  WHERE ftu_order_v.c_order_id = ol.c_order_id AND ol.qtyinvoiced <> ol.qtyordered))
        UNION ALL
         SELECT glj.ad_client_id,
            glj.ad_org_id,
            glj.documentno,
            glj.gl_journal_id AS record_id,
            glj.c_bpartner_id,
            glj.datedoc,
            glj.dateacct,
            sum(gljl.amtsourcecr) - COALESCE(t_1.payamt, 0::numeric) AS openamt,
            glj.c_doctype_id,
            'N'::bpchar AS issotrx,
            'GLJ'::text AS requesttype,
            glj.datedoc AS duedate,
            NULL::numeric AS grandtotal,
            0::numeric AS prepaidamt,
            0::numeric AS allocatedamt,
            glj.docstatus,
            glj.c_currency_id,
            glj.c_conversiontype_id
           FROM gl_journal glj
             JOIN gl_journalline gljl ON glj.gl_journal_id = gljl.gl_journal_id
             JOIN ( SELECT DISTINCT dt.account_id
                   FROM c_doctype dt
                  WHERE dt.isactive = 'Y'::bpchar) dtacc ON dtacc.account_id = gljl.account_id
             LEFT JOIN ( SELECT prl.gl_journal_id,
                    sum(prl.payamt) AS payamt
                   FROM ftu_paymentrequestline prl
                     JOIN ftu_paymentrequest pr ON pr.ftu_paymentrequest_id = prl.ftu_paymentrequest_id
                  WHERE pr.docstatus::text = ANY (ARRAY['CO'::character varying::text, 'CL'::character varying::text])
                  GROUP BY prl.gl_journal_id) t_1 ON t_1.gl_journal_id = glj.gl_journal_id
          WHERE glj.c_bpartner_id IS NOT NULL
          GROUP BY glj.ad_client_id, glj.ad_org_id, glj.documentno, glj.gl_journal_id, glj.c_bpartner_id, glj.datedoc, glj.dateacct, glj.c_doctype_id, glj.docstatus, glj.c_currency_id, glj.c_conversiontype_id, t_1.payamt) t
     JOIN c_bpartner bp ON t.c_bpartner_id = bp.c_bpartner_id;