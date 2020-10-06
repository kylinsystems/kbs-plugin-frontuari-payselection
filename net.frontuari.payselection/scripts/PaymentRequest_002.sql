alter table c_order add column prepaymentinvdisc char(2) default null;

CREATE OR REPLACE VIEW adempiere.ftu_rv_order_v AS 
SELECT o.ad_client_id,
    o.ad_org_id,
    o.ad_orgtrx_id,
    o.ad_user_id,
    o.amountrefunded,
    o.amounttendered,
    o.bill_bpartner_id,
    o.bill_location_id,
    o.bill_user_id,
    o.c_activity_id,
    o.c_bpartner_id,
    o.c_bpartner_location_id,
    o.c_campaign_id,
    o.c_cashline_id,
    o.c_charge_id,
    o.c_conversiontype_id,
    o.c_currency_id,
    o.c_doctype_id,
    o.c_doctypetarget_id,
    o.chargeamt,
    o.copyfrom,
    o.c_order_id,
    o.c_ordersource_id,
    o.c_payment_id,
    o.c_paymentterm_id,
    o.c_pos_id,
    o.c_project_id,
    o.created,
    o.createdby,
    o.dateacct,
    o.dateordered,
    o.dateprinted,
    o.datepromised,
    o.deliveryrule,
    o.deliveryviarule,
    o.description,
    o.docaction,
    o.docstatus,
    o.documentno,
    o.dropship_bpartner_id,
    o.dropship_location_id,
    o.dropship_user_id,
    o.freightamt,
    o.freightcostrule,
        CASE
            WHEN o.ispayschedulevalid = 'N'::bpchar THEN o.grandtotal
            ELSE pps.dueamt
        END AS grandtotal,
    o.invoicerule,
    o.isactive,
    o.isapproved,
    o.iscreditapproved,
    o.isdelivered,
    o.isdiscountprinted,
    o.isdropship,
    o.isinvoiced,
    o.ispayschedulevalid,
    o.isprinted,
    o.isselected,
    o.isselfservice,
    o.issotrx,
    o.istaxincluded,
    o.istransferred,
    o.link_order_id,
    o.m_freightcategory_id,
    o.m_pricelist_id,
    o.m_shipper_id,
    o.m_warehouse_id,
    o.ordertype,
    o.pay_bpartner_id,
    o.pay_location_id,
    o.paymentrule,
    o.poreference,
    o.posted,
    o.priorityrule,
    o.processed,
    o.processedon,
    o.processing,
    o.promotioncode,
    o.ref_order_id,
    o.salesrep_id,
    o.sendemail,
    o.totallines,
    o.updated,
    o.updatedby,
    o.user1_id,
    o.user2_id,
    o.volume,
    o.weight,
    pps.c_orderpayschedule_id,
    pps.dueamt,
    pps.duedate
   FROM c_order o
     LEFT JOIN c_orderpayschedule pps ON pps.c_order_id = o.c_order_id;

CREATE OR REPLACE VIEW adempiere.ftu_rv_openpayment AS 
SELECT t.ad_client_id,
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
    t.docstatus
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
            COALESCE(payinv.payamt,
                CASE
                    WHEN o.prepaymentinvdisc = 'OI'::bpchar THEN pay.payamt
                    WHEN o.prepaymentinvdisc = 'AI'::bpchar THEN payall.payamt
                    WHEN o.prepaymentinvdisc IS NULL THEN payall.payamt
                    ELSE 0::numeric
                END, 0::numeric) AS prepaidamt,
            COALESCE(rv_openitem.paidamt, 0::numeric) AS allocatedamt,
            rv_openitem.docstatus
           FROM rv_openitem
             LEFT JOIN c_order o ON rv_openitem.c_order_id = o.c_order_id
             LEFT JOIN ( SELECT inv.c_invoice_id,
                    sum(p.payamt) AS payamt
                   FROM c_payment p
                     JOIN c_order o_1 ON p.c_order_id = o_1.c_order_id
                     JOIN ( SELECT i.c_invoice_id,
                            ol.c_order_id
                           FROM c_invoice i
                             JOIN c_invoiceline il ON i.c_invoice_id = il.c_invoice_id
                             JOIN c_orderline ol ON il.c_orderline_id = ol.c_orderline_id) inv ON inv.c_order_id = o_1.c_order_id
                  WHERE (p.docstatus = ANY (ARRAY['CO'::bpchar, 'CL'::bpchar])) AND p.isallocated = 'N'::bpchar
                  GROUP BY inv.c_invoice_id
                 HAVING count(DISTINCT o_1.prepaymentinvdisc) > 1) payinv ON payinv.c_invoice_id = rv_openitem.c_invoice_id
             LEFT JOIN ( SELECT p.c_order_id,
                    sum(p.payamt) AS payamt
                   FROM c_payment p
                     JOIN c_order o_1 ON p.c_order_id = o_1.c_order_id
                  WHERE (p.docstatus = ANY (ARRAY['CO'::bpchar, 'CL'::bpchar])) AND p.isallocated = 'N'::bpchar AND o_1.prepaymentinvdisc = 'OI'::bpchar
                  GROUP BY p.c_order_id) pay ON pay.c_order_id = rv_openitem.c_order_id
             LEFT JOIN ( SELECT p.c_bpartner_id,
                    sum(p.payamt) AS payamt
                   FROM c_payment p
                     JOIN c_order o_1 ON p.c_order_id = o_1.c_order_id
                  WHERE (p.docstatus = ANY (ARRAY['CO'::bpchar, 'CL'::bpchar])) AND p.isallocated = 'N'::bpchar AND o_1.prepaymentinvdisc = 'AI'::bpchar
                  GROUP BY p.c_bpartner_id) payall ON payall.c_bpartner_id = rv_openitem.c_bpartner_id
        UNION ALL
         SELECT ftu_rv_order_v.ad_client_id,
            ftu_rv_order_v.ad_org_id,
            ftu_rv_order_v.documentno,
            ftu_rv_order_v.c_order_id AS record_id,
            ftu_rv_order_v.c_bpartner_id,
            ftu_rv_order_v.dateordered AS datedoc,
            ftu_rv_order_v.dateacct,
            paymentrequestopen('POO'::character varying, ftu_rv_order_v.c_order_id, ftu_rv_order_v.c_orderpayschedule_id) AS openamt,
            ftu_rv_order_v.c_doctype_id,
            ftu_rv_order_v.issotrx,
            'POO'::text AS requesttype,
            ftu_rv_order_v.duedate,
            ftu_rv_order_v.grandtotal,
            COALESCE(pay.payamt, 0::numeric) AS prepaidamt,
            0::numeric AS allocatedamt,
            ftu_rv_order_v.docstatus
           FROM ftu_rv_order_v
             LEFT JOIN ( SELECT c_payment.c_order_id,
                    sum(c_payment.payamt) AS payamt
                   FROM c_payment
                  WHERE (c_payment.docstatus = ANY (ARRAY['CO'::bpchar, 'CL'::bpchar])) AND c_payment.isallocated = 'N'::bpchar
                  GROUP BY c_payment.c_order_id) pay ON pay.c_order_id = ftu_rv_order_v.c_order_id
          WHERE ftu_rv_order_v.docstatus = 'WP'::bpchar OR ftu_rv_order_v.docstatus = 'CO'::bpchar AND (EXISTS ( SELECT 1
                   FROM c_doctype dt
                  WHERE ftu_rv_order_v.c_doctype_id = dt.c_doctype_id AND (dt.docsubtypeso = 'SO'::bpchar OR dt.docbasetype = 'POO'::bpchar))) AND (EXISTS ( SELECT 1
                   FROM c_orderline ol
                  WHERE ftu_rv_order_v.c_order_id = ol.c_order_id AND ol.qtyinvoiced <> ol.qtyordered))
        UNION ALL
         SELECT gljb.ad_client_id,
            gljb.ad_org_id,
            (gljb.documentno::text || '-'::text) || gljl.line AS documentno,
            gljl.gl_journalline_id AS record_id,
            gljl.c_bpartner_id,
            gljb.datedoc,
            gljb.dateacct,
            gljl.amtsourcedr - gljl.amtsourcecr - COALESCE(t_1.payamt, 0::numeric) AS openamt,
            gljb.c_doctype_id,
            'N'::bpchar AS issotrx,
            'GLJ'::text AS requesttype,
            gljb.datedoc AS duedate,
            NULL::numeric AS grandtotal,
            0::numeric AS prepaidamt,
            0::numeric AS allocatedamt,
            gljb.docstatus
           FROM gl_journalbatch gljb
             JOIN gl_journal glj ON gljb.gl_journalbatch_id = glj.gl_journalbatch_id
             JOIN gl_journalline gljl ON glj.gl_journal_id = gljl.gl_journal_id
             LEFT JOIN ( SELECT prl.gl_journalline_id,
                    sum(prl.payamt) AS payamt
                   FROM ftu_paymentrequestline prl
                     JOIN ftu_paymentrequest pr ON pr.ftu_paymentrequest_id = prl.ftu_paymentrequest_id
                  WHERE pr.docstatus::text = 'CO'::text OR pr.docstatus IS NULL
                  GROUP BY prl.gl_journalline_id) t_1 ON t_1.gl_journalline_id = gljl.gl_journalline_id
          WHERE gljl.c_bpartner_id IS NOT NULL) t
     JOIN c_bpartner bp ON t.c_bpartner_id = bp.c_bpartner_id;