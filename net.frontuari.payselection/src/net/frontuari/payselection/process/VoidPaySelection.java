package net.frontuari.payselection.process;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionLine;
import net.frontuari.payselection.base.FTUProcess;
import net.frontuari.payselection.model.MFTUPaymentRequestLine;

public class VoidPaySelection extends FTUProcess {

	@Override
	protected void prepare() {
		
	}

	@Override
	protected String doIt() throws Exception {
		MPaySelection ps = new MPaySelection(getCtx(), getRecord_ID(), get_TrxName());
		
		for(MPaySelectionLine line : ps.getLines(true))
		{
			if(line.getC_PaySelectionCheck().getC_Payment_ID() > 0 
					&& line.getC_PaySelectionCheck().getC_Payment().getDocStatus().equals("CO"))
				throw new AdempiereException("@payselectionline.cannot.void@");
			
			line.setIsActive(false);
			line.saveEx(get_TrxName());
			if(line.get_ValueAsInt("FTU_PaymentRequest_ID") > 0)
			{
				MFTUPaymentRequestLine prl = new MFTUPaymentRequestLine(getCtx(), line.get_ValueAsInt("FTU_PaymentRequest_ID"), get_TrxName());
				prl.setIsPrepared(false);
				prl.saveEx(get_TrxName());
			}
		}
		
		ps.setIsActive(false);
		ps.set_ValueOfColumn("VoidPaySelection","Y");
		ps.saveEx(get_TrxName());
		
		return "@payselection.void@";
	}

}
