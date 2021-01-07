
CREATE OR REPLACE FUNCTION allocatePaySelectionAmt(p_FTU_PaymentRequestLine_ID NUMERIC)
RETURNS NUMERIC
LANGUAGE plpgsql
AS $$
DECLARE
	v_RetVal NUMERIC := 0;
BEGIN
	SELECT
		SUM(COALESCE(currencyconvert(cp.payamt, cb.c_currency_id, fp2.c_currency_id, cp2.paydate
				, COALESCE(ci.c_conversiontype_id, co.c_conversiontype_id, gj.c_conversiontype_id, ca2.c_conversiontype_id)
				, fp2.ad_client_id, fp2.ad_org_id), 0))
		INTO
		v_RetVal
	FROM c_payselectionline cp 
	INNER JOIN c_payselection cp2 ON cp2.c_payselection_id = cp.c_payselection_id 
	INNER JOIN ftu_paymentrequestline fp ON fp.ftu_paymentrequestline_id = cp.ftu_paymentrequestline_id 
	INNER JOIN ftu_paymentrequest fp2 ON fp2.ftu_paymentrequest_id = fp.ftu_paymentrequest_id 
	INNER JOIN c_bankaccount cb ON cb.c_bankaccount_id = cp2.c_payselection_id 
	LEFT JOIN c_invoice ci ON (ci.c_invoice_id = fp.c_invoice_id and fp2.requesttype IN ('API', 'ARI'))
	LEFT JOIN c_order co ON (co.c_order_id = fp.c_order_id AND fp2.requesttype = 'POO')
	LEFT JOIN gl_journal gj ON (gj.gl_journal_id = fp.gl_journal_id AND fp2.requesttype = 'GLJ')
	LEFT JOIN cop_assemblyrecordline ca ON (ca.cop_assemblyrecordline_id = fp.cop_assemblyrecordline_id
											AND fp2.requesttype = 'CAR')
	LEFT JOIN cop_assemblyrecord ca2 ON (ca2.cop_assemblyrecord_id = ca.cop_assemblyrecord_id
											AND fp2.requesttype = 'CAR')
	WHERE cp.ftu_paymentrequestline_id = p_FTU_PaymentRequestLine_ID
	AND cp.isactive = 'Y';
	
	return COALESCE(v_RetVal, 0);
END $$

