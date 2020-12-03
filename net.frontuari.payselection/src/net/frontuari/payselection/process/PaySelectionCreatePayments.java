package net.frontuari.payselection.process;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaymentBatch;
import org.compiere.process.ProcessInfoParameter;

import net.frontuari.payselection.base.FTUProcess;
import net.frontuari.payselection.model.FTUMPaySelectionCheck;

/**
 * 
 * @author Argenis Rodríguez
 *
 */
public class PaySelectionCreatePayments extends FTUProcess {

	private int p_C_PaySelection_ID = 0;
	private boolean p_IsCreateLotDeposit = false;
	private String p_PaymentRule = null;
	
	@Override
	protected void prepare() {
		
		for (ProcessInfoParameter parameter: getParameter())
		{
			String name = parameter.getParameterName();
			
			if ("C_PaySelection_ID".equals(name))
				p_C_PaySelection_ID = parameter.getParameterAsInt();
			else if ("IsCreateLotDeposit".equals(name))
				p_IsCreateLotDeposit = parameter.getParameterAsBoolean();
			else if ("PaymentRule".equals(name))
				p_PaymentRule = parameter.getParameterAsString();
		}
		
		if (p_C_PaySelection_ID <= 0)
			p_C_PaySelection_ID = getRecord_ID();
	}

	@Override
	protected String doIt() throws Exception {
		
		if (p_C_PaySelection_ID <= 0)
			throw new AdempiereException("@FillMandatory@ @C_PaySelection_ID@");
		
		MPaySelection paySelection = new MPaySelection(getCtx(), p_C_PaySelection_ID, get_TrxName());
		
		if (!paySelection.isProcessed())
			throw new AdempiereException("@C_PaySelection_ID@ @no@ @Processed@");
		
		FTUMPaySelectionCheck [] checks = FTUMPaySelectionCheck.get(p_C_PaySelection_ID, p_PaymentRule, get_TrxName());
		MPaymentBatch payBatch = MPaymentBatch.getForPaySelection(getCtx(), p_C_PaySelection_ID, get_TrxName());
		
		List<FTUMPaySelectionCheck> checksWithoutPayment = Arrays.stream(checks)
			.filter(check -> check.getC_Payment_ID() <= 0)
			.collect(Collectors.toList());
		
		FTUMPaySelectionCheck.confirmPrint(checksWithoutPayment.toArray(new FTUMPaySelectionCheck[checksWithoutPayment.size()])
				, payBatch
				, p_IsCreateLotDeposit);
		
		return "@Created@ = " + checksWithoutPayment.size();
	}

}
