--DROP VIEW FTU_RV_OpenPayment;
CREATE OR REPLACE VIEW FTU_RV_OpenPayment AS
SELECT 
	t.AD_Client_ID,
	t.AD_Org_ID,
	DocumentNo,
	Record_ID,
	t.C_BPartner_ID,
	DateDoc,
	DateAcct,
	OpenAmt,
	C_DocType_ID,
	IsSOTrx,
	RequestType,
	DueDate,
	bp.Name
	--2014-11-24 Carlos Parada Add Prepay Amt And Allocate Amt
	,PrepaidAmt,
	AllocatedAmt,
	--End Carlos Parada
	GrandTotal,
	--2015-05-30 Carlos Parada Add DocStatus
	DocStatus
	--End Carlos Parada
	,C_Currency_ID
	,C_ConversionType_ID 
FROM  (
SELECT 
	AD_Client_ID,
	AD_Org_ID,
	DocumentNo,
	RV_OpenItem.C_Invoice_ID AS Record_ID,
	C_BPartner_ID,
	DateInvoiced AS DateDoc,
	DateAcct,
	--OpenAmt,	
	PaymentRequestOpen('API',RV_OpenItem.C_Invoice_ID,C_InvoicePaySchedule_ID) OpenAmt,	
	C_DocType_ID,
	IsSOTrx,
	'API' AS RequestType,
	DueDate,
	GrandTotal,
	--2014-11-24 Carlos Parada Add Prepay Amt And Allocate Amt
	COALESCE(pay.PayAmt,0)::numeric as prepaidamt,
	COALESCE(RV_OpenItem.PaidAmt,0)::numeric AllocatedAmt,
	--End Carlos Parada
	--2015-05-30 Carlos Parada Add DocStatus
	RV_OpenItem.DocStatus
	--End Carlos Parada
   	,RV_OpenItem.C_Currency_ID
   	,RV_OpenItem.C_ConversionType_ID 
FROM RV_OpenItem
/** 2014-11-24 Carlos Parada Add Prepay Amt And Allocate Amt*/
LEFT JOIN (
	SELECT 
		C_Order_ID,
		SUM(PayAmt) As PayAmt 
	FROM C_Payment 
	WHERE 
		DocStatus IN ('CO','CL') 
		AND IsAllocated ='N' 
	GROUP BY C_Order_ID
) pay ON pay.C_Order_ID = RV_OpenItem.C_Order_ID
/** End Carlos Parada*/
UNION ALL
SELECT
	AD_Client_ID,
	AD_Org_ID,
	DocumentNo,
	C_Order_ID AS Record_ID,
	C_BPartner_ID,
	DateOrdered AS DateDoc,
	DateAcct,
	PaymentRequestOpen('POO',C_Order_ID,C_OrderPaySchedule_ID) AS OpenAmt,
	C_DocType_ID,
	IsSOTrx,
	'POO' AS RequestType,
	DueDate,
	GrandTotal,
	--2014-11-24 Carlos Parada Add Prepay Amt And Allocate Amt
	0::numeric as prepaidamt,
	0::numeric AllocatedAmt,
	--End Carlos Parada
	--2015-05-30 Carlos Parada Add DocStatus
	FTU_Order_V.DocStatus
	--End Carlos Parada
   	,FTU_Order_V.C_Currency_ID
   	,FTU_Order_V.C_ConversionType_ID
FROM FTU_Order_V
/** 2014-11-24 Carlos Parada Add Prepay Amt And Allocate Amt*/
WHERE 
	FTU_Order_V.DocStatus='WP' 
	OR (
		FTU_Order_V.DocStatus='CO' 
		AND EXISTS (
			SELECT 1 
			FROM C_DocType dt 
			WHERE 
				FTU_Order_V.C_DocType_ID=dt.C_DocType_ID 
				AND (dt.DocSubTypeSO='SO' OR dt.DocBaseType='POO')
			)
		AND EXISTS (
			SELECT 1 
			FROM C_OrderLine ol 
			WHERE 
				FTU_Order_V.C_Order_ID=ol.C_Order_ID 
				AND ol.QtyInvoiced<>ol.QtyOrdered
		)
	)/** End Carlos Parada*/
UNION ALL
SELECT 
	gljb.AD_Client_ID,
	gljb.AD_Org_ID,
	gljb.DocumentNo ||'-'||gljl.Line AS DocumentNO,
	gljl.GL_JournalLine_ID AS Record_ID,
	gljl.C_BPartner_ID,
	gljb.DateDoc,
	gljb.DateAcct,
	(gljl.AmtSourceDR - gljl.AmtSourceCR) - COALESCE(t.PayAmt,0) OpenAmt,
	gljb.C_DocType_ID,
	'N' IsSOTrx,
	'GLJ' AS RequestType,
	gljb.DateDoc DueDate,
	NULL GrandTotal,
	--2014-11-24 Carlos Parada Add Prepay Amt And Allocate Amt
	0::numeric as prepaidamt,
	0::numeric AllocatedAmt,
	--End Carlos Parada
	--2015-05-30 Carlos Parada Add DocStatus
	gljb.DocStatus
	--End Carlos Parada
   	,glj.C_Currency_ID
   	,glj.C_ConversionType_ID
FROM GL_JournalBatch gljb
INNER JOIN GL_Journal glj ON (gljb.GL_JournalBatch_ID = glj.GL_JournalBatch_ID)
INNER JOIN GL_JournalLine gljl ON (glj.GL_Journal_ID = gljl.GL_Journal_ID)
LEFT JOIN (
	SELECT GL_JournalLine_ID,SUM(prl.PayAmt) PayAmt
	FROM FTU_PaymentRequestLine prl 
	INNER JOIN FTU_PaymentRequest pr ON (pr.FTU_PaymentRequest_ID = prl.FTU_PaymentRequest_ID )
	WHERE 
		(pr.DocStatus ='CO' OR  pr.DocStatus IS NULL)
	GROUP BY 
		GL_JournalLine_ID

) t ON (t.GL_JournalLine_ID = gljl.GL_JournalLine_ID) 
WHERE
	gljl.C_BPartner_ID IS NOT NULL
) t
INNER JOIN C_BPartner bp ON (t.C_BPartner_ID = bp.C_BPartner_ID );