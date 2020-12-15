
CREATE OR REPLACE FUNCTION paymentRequestOpenByAssemblyRecordLine(p_COP_AssemblyRecordLine_ID NUMERIC)
	RETURNS NUMERIC
	LANGUAGE plpgsql
AS $$
DECLARE
	v_TotalOpenAmt NUMERIC := 0;
			 v_Min NUMERIC := 0;
	 v_Currency_ID NUMERIC := 0;
	     v_PaidAmt NUMERIC := 0;
	   v_Precision NUMERIC := 0;
   v_PayAmtRequest NUMERIC := 0;
	                  ar RECORD;
BEGIN
	BEGIN
		SELECT
			ca2.c_currency_id
			, ftu_assemblyrecordopen(ca.cop_assemblyrecordline_id)
			, cc.stdprecision 
			, ca.payamt 
			INTO
			v_Currency_ID
			, v_TotalOpenAmt
			, v_Precision
			, v_PaidAmt
		FROM cop_assemblyrecordline ca 
		INNER JOIN cop_assemblyrecord ca2 ON (ca2.cop_assemblyrecord_id = ca.cop_assemblyrecord_id)
		INNER JOIN c_currency cc ON (cc.c_currency_id = ca2.c_currency_id)
		WHERE ca.cop_assemblyrecordline_id = p_COP_AssemblyRecordLine_ID;
	EXCEPTION
		WHEN OTHERS THEN
		RAISE NOTICE 'Payment Request Open - %', SQLERRM;
			RETURN NULL;
	END;
	
	v_PaidAmt := v_PaidAmt - v_TotalOpenAmt;
	
	v_Min := 1/10^v_Precision;
	
	SELECT
		COALESCE(SUM(currencyconvert(fp.payamt, fp2.c_currency_id, v_Currency_ID, fp2.datedoc, null
			, fp.ad_client_id, fp.ad_org_id)), 0)
		INTO
		v_PayAmtRequest
	FROM ftu_paymentrequestline fp 
	INNER JOIN ftu_paymentrequest fp2 ON (fp2.ftu_paymentrequest_id= fp.ftu_paymentrequest_id)
	LEFT JOIN c_payselectionline cp ON (cp.ftu_paymentrequestline_id = fp.ftu_paymentrequestline_id)
	LEFT JOIN c_payselectioncheck cp2 ON (cp2.c_payselectioncheck_id = cp.c_payselectioncheck_id)
	WHERE fp.cop_assemblyrecordline_id = p_COP_AssemblyRecordLine_ID
	AND fp2.requesttype = 'CAR'
	AND fp2.docstatus = 'CO'
	AND cp2.c_payment_id IS NULL;
	
	v_PaidAmt := v_PaidAmt + v_PayAmtRequest;
	
	v_TotalOpenAmt := v_TotalOpenAmt - v_PayAmtRequest;
	v_TotalOpenAmt := COALESCE(ROUND(v_TotalOpenAmt, v_Precision), 0);
	RETURN v_TotalOpenAmt;
END; $$