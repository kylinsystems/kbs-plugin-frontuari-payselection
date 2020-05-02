package net.frontuari.payselection.model;

import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MDocType;
import org.compiere.model.MOrder;
import org.compiere.model.MPaySelectionLine;
import org.compiere.model.X_C_DocType;
import org.compiere.model.X_C_PaySelectionLine;

import net.frontuari.payselection.base.FTUEvent;

public class FTUPaySelectionEvents extends FTUEvent {

	@Override
	protected void doHandleEvent() {
		String tableName = getPO().get_TableName();
		String eventType = getEventType();
		if(eventType.equals(IEventTopics.PO_AFTER_NEW))
		{
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
		if(eventType.equals(IEventTopics.PO_BEFORE_NEW) || eventType.equals(IEventTopics.PO_BEFORE_CHANGE))
		{
			if(tableName.equals(X_C_DocType.Table_Name))
			{
				MDocType dt = (MDocType) getPO();
				//	Validate that field IsManual only when DocBaseType = PSO and IsOrderPrePayment its false
				if(dt.getDocBaseType().equals("PSO"))
				{
					if(dt.get_ValueAsBoolean("IsOrderPrePayment") 
							&& dt.get_ValueAsBoolean("IsManual"))
						dt.set_ValueOfColumn("IsManual", "N");
				}
				
			}
		}
	}

}
