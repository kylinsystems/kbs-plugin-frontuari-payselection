CREATE OR REPLACE FUNCTION paymentrequestopen(p_requesttype character varying, p_record_id numeric, p_recordpayschedule_id numeric)
  RETURNS numeric AS
$BODY$
DECLARE
	v_TotalOpenAmt  	NUMERIC := 0;
	v_C_Invoice_ID		NUMERIC := 0;
    	v_C_InvoicePaySchedule_ID NUMERIC := 0; 
    	v_Min            	NUMERIC := 0;
    	v_PayAmtOrg		NUMERIC	:= 0;
    	v_PayAmt		NUMERIC	:= 0;
    	v_Result		NUMERIC	:= 0;
    	v_Currency_ID		NUMERIC	:= 0;
    	v_MultiplierAP		NUMERIC	:= 1;
    	v_MultiplierCM		NUMERIC	:= 1;
    	v_MultiplierAPI		NUMERIC	:= -1;
    	v_PayAmtRequest		NUMERIC	:= 0;
    	v_MultiplierPO		NUMERIC	:= 1;
    	v_Precision		NUMERIC	:= 0;
    	ar			RECORD;
    	s			RECORD;
    	v_Temp 			NUMERIC	:= 0;
    	v_PaidAmt		NUMERIC	:= 0;
    	v_Remaining 		NUMERIC	:= 0;
    	v_DueAmt		NUMERIC	:= 0;
    	v_CurrencyConvert	NUMERIC := 0;
BEGIN

	BEGIN
		SELECT	C_Currency_ID, InvoiceOpen(RV_C_Invoice.C_Invoice_ID,p_RecordPaySchedule_ID)  OpenAmt, 
			InvoicePaid(RV_C_Invoice.C_Invoice_ID,C_Currency_ID,1) * v_MultiplierAPI PaidAmt
		INTO	v_Currency_ID, v_TotalOpenAmt,v_PaidAmt
		FROM	RV_C_Invoice
		WHERE	RV_C_Invoice.C_Invoice_ID = p_Record_ID AND p_RequestType = 'API'
		UNION ALL 
		SELECT	C_Currency_ID,OrderOpen(C_Order_ID, p_RecordPaySchedule_ID) AS OpenAmt,
		Sum(GrandTotal) - OrderOpen(C_Order_ID, 0) AS PaidAmt 
		FROM	FTU_Order_V
		WHERE	C_Order_ID = p_Record_ID AND p_RequestType = 'POO'
		Group By C_Currency_ID,C_Order_ID;
	EXCEPTION	
		WHEN OTHERS THEN
		RAISE NOTICE 'InvoiceOpen - %', SQLERRM;
			RETURN NULL;
	END;

	RAISE NOTICE 'v_TotalOpenAmt,v_PaidAmt %,% ',v_TotalOpenAmt,v_PaidAmt;

	SELECT StdPrecision
	    INTO v_Precision
	    FROM C_Currency
	    WHERE C_Currency_ID = v_Currency_ID;

	SELECT 1/10^v_Precision INTO v_Min;

	FOR ar IN 
	SELECT prl.AD_Client_ID, prl.AD_Org_ID, SUM(prl.PayAmt) PayAmt,pr.C_Currency_ID,pr.DateDoc--, p.PayAmt PaymenAmt
		FROM FTU_PaymentRequestLine prl 
		INNER JOIN FTU_PaymentRequest pr ON (pr.FTU_PaymentRequest_ID = prl.FTU_PaymentRequest_ID)
		LEFT JOIN C_PaySelectionLine psl ON (psl.FTU_PaymentRequestLine_ID = prl.FTU_PaymentRequestLine_ID)
		LEFT  JOIN C_PaySelectionCheck psc ON (psc.C_PaySelectionCheck_ID = psl.C_PaySelectionCheck_ID)
		WHERE 
			((prl.C_Invoice_ID = p_Record_ID AND p_RequestType = 'API')
			OR
			 (prl.C_Order_ID = p_Record_ID AND p_RequestType = 'POO'))
			AND pr.DocStatus = 'CO'
			AND psc.C_Payment_ID IS NULL
		GROUP BY
			prl.AD_Client_ID, prl.AD_Org_ID,pr.C_Currency_ID,DateDoc--,p.PayAmt


	LOOP
		v_Temp := ar.PayAmt;
		v_PayAmtRequest := v_PayAmtRequest + currencyConvert(v_Temp * v_MultiplierCM,
				v_Currency_ID, --ar.C_Currency_ID, 
				v_Currency_ID, ar.DateDoc, null, ar.AD_Client_ID, ar.AD_Org_ID); 
		
		v_PaidAmt := v_PaidAmt + v_PayAmtRequest;

	END LOOP;
	IF (p_RecordPaySchedule_ID > 0) THEN --   if not valid = lists invoice amount
	  v_Remaining := v_PaidAmt;
	  FOR s IN 
		  SELECT  C_InvoicePaySchedule_ID AS RecordPaySchedule_ID, DueAmt,DueDate
		  FROM    C_InvoicePaySchedule
		  WHERE	C_Invoice_ID = p_Record_ID
		  AND   IsValid='Y' AND p_RequestType = 'API'
		  UNION ALL 
		  SELECT  C_OrderPaySchedule_ID AS RecordPaySchedule_ID, DueAmt,DueDate
		  FROM    C_OrderPaySchedule
		  WHERE	C_Order_ID = p_Record_ID
		  AND   IsValid='Y' AND p_RequestType = 'POO'
		  ORDER BY 3
		  
	  LOOP
		  IF (s.RecordPaySchedule_ID = p_RecordPaySchedule_ID) THEN
		  v_TotalOpenAmt := (s.DueAmt*v_MultiplierCM) - v_Remaining;
		  IF (s.DueAmt - v_Remaining < 0) THEN
		      v_TotalOpenAmt := 0;
		  END IF;
	      ELSE -- calculate amount, which can be allocated to next schedule
		  v_Remaining := v_Remaining - s.DueAmt;
		  IF (v_Remaining < 0) THEN
		      v_Remaining := 0;
		  END IF;
	      END IF;
	  END LOOP;
	ELSE
		RAISE NOTICE 'v_TotalOpenAmt %d', v_TotalOpenAmt;
		RAISE NOTICE 'v_PaidAmt %d', v_PaidAmt;
	    --v_TotalOpenAmt := v_TotalOpenAmt - v_PaidAmt;
	     v_TotalOpenAmt := v_TotalOpenAmt - v_PayAmtRequest;
	END IF;
    
    IF (COALESCE(v_TotalOpenAmt,0) > -v_Min AND COALESCE(v_TotalOpenAmt,0) < v_Min) THEN
	    v_TotalOpenAmt := 0;
    END IF;
    --	Round to currency precision
    v_TotalOpenAmt := ROUND(COALESCE(v_TotalOpenAmt,0), COALESCE(v_Precision,0));	

    RAISE NOTICE 'v_TotalOpenAmt % ',v_TotalOpenAmt;
    RETURN	v_TotalOpenAmt;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE
  COST 100;
ALTER FUNCTION paymentrequestopen(character varying, numeric, numeric)
  OWNER TO adempiere;