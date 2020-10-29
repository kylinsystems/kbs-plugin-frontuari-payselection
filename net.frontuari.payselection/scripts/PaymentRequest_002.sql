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