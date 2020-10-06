CREATE OR REPLACE FUNCTION adempiere.orderopen(p_c_order_id numeric, p_c_orderpayschedule_id numeric)
 RETURNS numeric
 LANGUAGE plpgsql
AS $function$

DECLARE
	v_Currency_ID		NUMERIC(10);
	v_TotalOpenAmt  	NUMERIC := 0;
	v_PaidAmt  	        NUMERIC := 0;
	v_Remaining	        NUMERIC := 0;
    	v_Temp              	NUMERIC := 0;
    	v_Precision            	NUMERIC := 0;
    	v_Min            	NUMERIC := 0;
    	ar			RECORD;
    	s			RECORD;

BEGIN
		BEGIN
		SELECT	MAX(C_Currency_ID), SUM(GrandTotal)
		INTO	v_Currency_ID, v_TotalOpenAmt
		FROM	FTU_Order_V				
        WHERE	C_Order_ID = p_C_Order_ID
                AND IsInvoiced = 'N';
        	EXCEPTION			WHEN OTHERS THEN
            	RAISE NOTICE 'orderOpen - %', SQLERRM;
			RETURN NULL;
	END;

	SELECT StdPrecision
	    INTO v_Precision
	    FROM C_Currency
	    WHERE C_Currency_ID = v_Currency_ID;

	SELECT 1/10^v_Precision INTO v_Min;

		FOR ar IN 
		SELECT	pa.AD_Client_ID, pa.AD_Org_ID,
		pa.PayAmt, pa.DiscountAmt, pa.WriteOffAmt,
		pa.C_Currency_ID, pa.DateTrx
		FROM	C_Payment pa
		WHERE	pa.C_Order_ID = p_C_Order_ID 
                        --AND pa.IsAllocated = 'N' 
                      	AND   pa.DocStatus IN('CO', 'CL')
	LOOP
        v_Temp := ar.PayAmt + ar.DisCountAmt + ar.WriteOffAmt;
		v_PaidAmt := v_PaidAmt
        			+ currencyConvert(v_Temp,
				ar.C_Currency_ID, v_Currency_ID, ar.DateTrx, null, ar.AD_Client_ID, ar.AD_Org_ID);
      	RAISE NOTICE '   PaidAmt=% , Paymen= %', v_PaidAmt, v_Temp;
	END LOOP;

        IF (p_C_OrderPaySchedule_ID > 0) THEN         v_Remaining := v_PaidAmt;
        FOR s IN 
        	SELECT  C_OrderPaySchedule_ID, DueAmt
	        FROM    C_OrderPaySchedule
		WHERE	C_Order_ID = p_C_Order_ID
	        AND   IsValid='Y'
        	ORDER BY DueDate
        LOOP
            IF (s.C_OrderPaySchedule_ID = p_C_OrderPaySchedule_ID) THEN
                v_TotalOpenAmt := s.DueAmt - v_Remaining;
                IF (s.DueAmt - v_Remaining < 0) THEN
                    v_TotalOpenAmt := 0;
                END IF;
            ELSE                 v_Remaining := v_Remaining - s.DueAmt;
                IF (v_Remaining < 0) THEN
                    v_Remaining := 0;
                END IF;
            END IF;
        END LOOP;
    ELSE
        v_TotalOpenAmt := v_TotalOpenAmt - v_PaidAmt;
    END IF;

		IF (v_TotalOpenAmt > -v_Min AND v_TotalOpenAmt < v_Min) THEN
		v_TotalOpenAmt := 0;
	END IF;

		v_TotalOpenAmt := ROUND(COALESCE(v_TotalOpenAmt,0), v_Precision);
	RETURN	v_TotalOpenAmt;
END;

$function$
;
