CREATE OR REPLACE FUNCTION FTU_AssemblyRecordOpen(p_COP_AssemblyRecordLine_ID NUMERIC)
RETURNS NUMERIC
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
	v_Currency_ID NUMERIC(10) := 0;
	v_TotalOpenAmt NUMERIC := 0;
	v_PaidAmt NUMERIC := 0;
	v_Precision NUMERIC := 0;
	v_Min NUMERIC := 0;
	ar RECORD;
BEGIN
	SELECT
		ca2.c_currency_id 
		, ca.payamt 
		, cc.stdprecision 
		INTO
		v_Currency_ID
		, v_TotalOpenAmt
		, v_Precision
	FROM cop_assemblyrecordline ca 
	INNER JOIN cop_assemblyrecord ca2 ON ca2.cop_assemblyrecord_id = ca.cop_assemblyrecord_id 
	INNER JOIN c_currency cc ON cc.c_currency_id = ca2.c_currency_id 
	WHERE ca.cop_assemblyrecordline_id = p_COP_AssemblyRecordLine_ID;
	
	v_Min := 1/10^v_Precision;
	
	FOR ar IN
		SELECT
			SUM(
				COALESCE(currencyconvert(cp.payamt + cp.discountamt + cp.writeoffamt
					, cp3.c_currency_id, v_Currency_ID, cp3.datetrx, null, cp3.ad_client_id, cp3.ad_org_id), 0)
			) AS convertedAmt
		FROM cop_assemblyrecordline ca 
		INNER JOIN ftu_paymentrequestline fp ON fp.cop_assemblyrecordline_id = ca.cop_assemblyrecordline_id 
		INNER JOIN c_payselectionline cp ON cp.ftu_paymentrequestline_id = fp.ftu_paymentrequestline_id 
		INNER JOIN c_payselectioncheck cp2 ON cp2.c_payselectioncheck_id = cp.c_payselectioncheck_id 
		INNER JOIN c_payment cp3 ON cp3.c_payment_id = cp2.c_payment_id 
		WHERE ca.cop_assemblyrecordline_id = p_COP_AssemblyRecordLine_ID
		AND cp3.docstatus IN ('CO', 'CL')
	LOOP
		v_PaidAmt := v_PaidAmt + ar.convertedAmt;
	END LOOP;
	
	v_TotalOpenAmt := v_TotalOpenAmt - COALESCE(v_PaidAmt, 0);
	--IGNORE ROUNDING
	
	IF (v_TotalOpenAmt > -v_Min AND v_TotalOpenAmt < v_Min) THEN
		v_TotalOpenAmt := 0;
	END IF;
	
	--Round to Currency Precision
	v_TotalOpenAmt = ROUND(COALESCE(v_TotalOpenAmt, 0), v_Precision);
	RETURN v_TotalOpenAmt;
END;$$
