package net.frontuari.payselection.model;

import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MOrder;
import org.compiere.model.MPaySelectionLine;
import org.compiere.model.X_C_PaySelectionLine;

import net.frontuari.payselection.base.FTUEvent;

public class FTUPaySelectionEvents extends FTUEvent {

	@Override
	protected void doHandleEvent() {
		if(getEventType().equals(IEventTopics.PO_AFTER_NEW))
		{
			String tableName = getPO().get_TableName();
			if(tableName.equals(X_C_PaySelectionLine.Table_Name))
			{
				MPaySelectionLine psl = (MPaySelectionLine) getPO();
				if(psl.get_ValueAsInt("C_BPartner_ID") <= 0)
				{
					int BPartnerID = 0; 
					if(psl.getC_Invoice_ID()>0)
						BPartnerID = psl.getC_Invoice().getC_BPartner_ID();
					else if(psl.get_ValueAsInt("C_Order_ID")>0)
					{
						MOrder ord = new MOrder(psl.getCtx(), psl.get_ValueAsInt("C_Order_ID"), null);
						BPartnerID = ord.getC_BPartner_ID();
					}
					
					psl.set_ValueOfColumn("C_BPartner_ID",BPartnerID);
					psl.saveEx();
				}
			}
		}
	}

}
