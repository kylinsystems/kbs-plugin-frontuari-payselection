package net.frontuari.payselection.model;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.adempiere.base.event.IEventTopics;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MDocType;
import org.compiere.model.MOrder;
import org.compiere.model.MPaySelectionLine;
import org.compiere.model.MPayment;
import org.compiere.model.Query;
import org.compiere.model.X_C_DocType;
import org.compiere.model.X_C_PaySelectionLine;
import org.compiere.util.DB;

import com.coposa.shareholders.model.MCOPAssemblyRecordLine;

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
		if (IEventTopics.DOC_AFTER_COMPLETE.equals(eventType)
				&& MPayment.Table_Name.equals(getPO().get_TableName()))
			paidCOPAssemblyRecord();
	}
	
	/**
	 * @author Argenis Rodríguez
	 * Test if Assembly Record Line is Paid
	 */
	private void paidCOPAssemblyRecord() {
		
		MPayment payment = (MPayment) getPO();
		
		List<MCOPAssemblyRecordLine> lines = new Query(payment.getCtx(), MCOPAssemblyRecordLine.Table_Name, "psc.C_Payment_ID = ?", payment.get_TrxName())
				.addJoinClause("INNER JOIN FTU_PaymentRequestLine prl ON (prl.COP_AssemblyRecordLine_ID = COP_AssemblyRecordLine.COP_AssemblyRecordLine_ID)")
				.addJoinClause("INNER JOIN C_PaySelectionLine psl ON (psl.FTU_PaymentRequestLine_ID = prl.FTU_PaymentRequestLine_ID)")
				.addJoinClause("INNER JOIN C_PaySelectionCheck psc ON (psc.C_PaySelectionCheck_ID = psl.C_PaySelectionCheck_ID)")
				.setOnlyActiveRecords(true)
				.setParameters(payment.get_ID())
				.list();
		
		for (MCOPAssemblyRecordLine line: lines)
			testIsPaid(line);
	}
	
	/**
	 * @author Argenis Rodríguez
	 * @param arl
	 */
	private static void testIsPaid(MCOPAssemblyRecordLine arl) {
		
		BigDecimal payAmt = getPayAmt(arl.get_ID(), arl.get_TrxName());
		boolean isPaid = arl.isPaid();
		boolean equals = arl.getPayAmt().compareTo(payAmt) == 0;
		boolean change = isPaid != equals;
		
		if (change)
		{
			arl.setIsPaid(equals);
			arl.saveEx();
		}
	}
	
	/**
	 * @author Argenis Rodríguez
	 * @param COP_AssemblyRecordLine_ID
	 * @param trxName
	 * @return
	 */
	private static BigDecimal getPayAmt(int COP_AssemblyRecordLine_ID, String trxName) {
		
		StringBuffer sql = new StringBuffer("SELECT")
					.append(" SUM(COALESCE(currencyconvert(psl.PayAmt + psl.DiscountAmt + psl.WriteOffAmt, pay.C_Currency_ID"
							+ ", ar.C_Currency_ID, pay.DateTrx, ar.C_ConversionType_ID, pay.AD_Client_ID, pay.AD_Org_ID), 0))")
				.append(" FROM COP_AssemblyRecordLine arl")
				.append(" INNER JOIN COP_AssemblyRecord ar ON (ar.COP_AssemblyRecord_ID = arl.COP_AssemblyRecord_ID)")
				.append(" INNER JOIN FTU_PaymentRequestLine prl ON (prl.COP_AssemblyRecordLine_ID = arl.COP_AssemblyRecordLine_ID)")
				.append(" INNER JOIN C_PaySelectionLine psl ON (psl.FTU_PaymentRequestLine_ID = prl.FTU_PaymentRequestLine_ID)")
				.append(" INNER JOIN C_PaySelection ps ON (ps.C_PaySelection_ID = psl.C_PaySelection_ID)")
				.append(" INNER JOIN C_BankAccount ba ON (ba.C_BankAccount_ID = ps.C_BankAccount_ID)")
				.append(" INNER JOIN C_PaySelectionCheck psc ON (psc.C_PaySelectionCheck_ID = psl.C_PaySelectionCheck_ID)")
				.append(" INNER JOIN C_Payment pay ON (pay.C_Payment_ID = psc.C_Payment_ID)")
				.append(" WHERE arl.COP_AssemblyRecordLine_ID = ? AND pay.DocStatus NOT IN ('VO', 'RE')");
		
		BigDecimal payAmt = DB.getSQLValueBD(trxName, sql.toString(), COP_AssemblyRecordLine_ID);
		
		return Optional.ofNullable(payAmt).orElse(BigDecimal.ZERO);
	}
}
