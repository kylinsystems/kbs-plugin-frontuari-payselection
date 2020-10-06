package net.frontuari.payselection.model;

import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MInvoice;
import org.compiere.model.MJournalLine;
import org.compiere.model.MOrder;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.DB;

public class MFTUPaymentRequestLine extends X_FTU_PaymentRequestLine {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8652963085515566982L;

	public MFTUPaymentRequestLine(Properties ctx, int FTU_PaymentRequestLine_ID, String trxName) {
		super(ctx, FTU_PaymentRequestLine_ID, trxName);
	}

	public MFTUPaymentRequestLine(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}
	
	@Override
	protected boolean afterSave(boolean newRecord, boolean success) {
		super.afterSave(newRecord, success);
		if(success)
			return updateHeader();
		return false;
	}//	End After Save
	
	@Override
	protected boolean afterDelete (boolean success) {
		super.afterDelete(success);
		if(success)
			return updateHeader();
		//	Return
		return false;
	} //	End After Delete
	
	@Override
	protected boolean beforeSave(boolean newRecord) {
		super.beforeSave(newRecord);
		int C_BPartner_ID = getC_BPartner_ID();
		//Validate Business Partner
		if ((getC_Invoice_ID()!=0)){
			MInvoice inv = new MInvoice(getCtx(), getC_Invoice_ID(), get_TrxName());
			 
			if(inv.getC_BPartner_ID()!=C_BPartner_ID)
				setC_BPartner_ID(inv.getC_BPartner_ID());
		}
		else if ((getC_Order_ID()!=0)){
			MOrder ord = new MOrder(getCtx(), getC_Order_ID(), get_TrxName());
			if(ord.getC_BPartner_ID()!=C_BPartner_ID)
				setC_BPartner_ID(ord.getC_BPartner_ID());
		}
		else if ((getGL_JournalLine_ID()!=0)){
			MJournalLine jl = new MJournalLine(getCtx(), getGL_JournalLine_ID(), get_TrxName());
			if(jl.get_ValueAsInt("C_BPartner_ID")!=C_BPartner_ID)
				setC_BPartner_ID(jl.get_ValueAsInt("C_BPartner_ID"));
		}
		//End Carlos Parada
		if(newRecord){
			int seqNo = DB.getSQLValue(get_TrxName(),"SELECT NVL(MAX(Line),0)+10 AS DefaultValue " +
				"FROM FTU_PaymentRequestLine WHERE FTU_PaymentRequest_ID= ?",getFTU_PaymentRequest_ID());
			this.setLine(seqNo);
		}
		if(newRecord
					|| is_ValueChanged(COLUMNNAME_C_Order_ID)
						|| is_ValueChanged(COLUMNNAME_C_Invoice_ID)
							|| is_ValueChanged(COLUMNNAME_GL_JournalLine_ID)){
							MFTUPaymentRequest m_FTUPaymentRequest = 
									new MFTUPaymentRequest(getCtx(), getFTU_PaymentRequest_ID(), get_TrxName());
							
							if(m_FTUPaymentRequest.getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_APInvoice)
									&& getC_Invoice_ID() == 0)	{
								throw new AdempiereException("@C_Invoice_ID@ @NotFound@");
							}else if(m_FTUPaymentRequest.getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_PurchaseOrder)
									&& getC_Order_ID() == 0)	{
								throw new AdempiereException("@C_Order_ID@ @NotFound@");
							}else if(m_FTUPaymentRequest.getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_GLJournal)
									&& getGL_JournalLine_ID()== 0)	{
								throw new AdempiereException("@GL_JournalLine_ID@ @NotFound@");
							}
						}

		//	Return
		return true;
	}//	End beforeSave
	
	/**
	 * Update Header
	 * @author <a href="mailto:jcolmenarez@frontuari.com">Jorge Colmenarez</a> 2020-10-03, 12:27
	 * @return boolean
	 */
	private boolean updateHeader(){
		//	Recalculate Header
		//	Update Payment Request Header
		String sql = "UPDATE FTU_PaymentRequest pr SET PayAmt=( " +
				"SELECT COALESCE(SUM(prl.PayAmt),0) FROM FTU_PaymentRequestLine prl " +
				"WHERE prl.FTU_PaymentRequest_ID=pr.FTU_PaymentRequest_ID) " +
				"WHERE pr.FTU_PaymentRequest_ID= " + getFTU_PaymentRequest_ID();
		//
		int no = DB.executeUpdate(sql, get_TrxName());
		if (no != 1)
			log.warning("(1) #" + no);
		//
		return no == 1;
	}	//	updateHeaderTax

	/**
	 * Get Payment Request Line 
	 * @author <a href="mailto:jcolmenarez@frontuari.com">Jorge Colmenarez</a> 2020-10-03, 12:27
	 * @param ctx
	 * @param po
	 * @param p_Record_ID
	 * @param requestType
	 * @return MFTUPaymentRequestLine
	 */
	public static MFTUPaymentRequestLine get(Properties ctx, PO po,
			int p_Record_ID, String requestType) {
		//
		String sqlWhere = null;
		//	Compare Request Type
		if(requestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_APInvoice)){
			sqlWhere = "C_Invoice_ID = ?";
		}else if(requestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_GLJournal)){
			sqlWhere = "GL_JournalLine_ID = ?";
		}else if(requestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_PurchaseOrder)){
			sqlWhere = "C_Order_ID = ?";
		}else
			sqlWhere = "";
		
		sqlWhere += sqlWhere.length() > 0 ? "AND FTU_PaymentRequest_ID = ?" : " FTU_PaymentRequest_ID = ?";
		
		//	Get record of db
		MFTUPaymentRequestLine line = 
				new Query(ctx, Table_Name, sqlWhere, po.get_TrxName()).
				setParameters(p_Record_ID, po.get_Value("FTU_PaymentRequest_ID")).
				first();
		//	Return
		return line;
	}
	
	/**
	 * Get Payment Request Line of Pay Selection Line
	 * @author <a href="mailto:jcolmenarez@frontuari.com">Jorge Colmenarez</a> 2020-10-03, 12:28
	 * @param ctx
	 * @param p_C_PaySelectionLine_ID
	 * @param trxName
	 * @return MFTUPaymentRequestLine
	 */
	public static MFTUPaymentRequestLine getOfPaySelectionLine(Properties ctx, int p_C_PaySelectionLine_ID, String trxName) {
		StringBuilder whereClause = new StringBuilder();
		whereClause.append("C_PaySelectionLine_ID=?");          // #1
		//
		MFTUPaymentRequestLine existingPaymentRequestLine = 
				new Query(ctx, X_FTU_PaymentRequestLine.Table_Name, whereClause.toString(), trxName)
						.setParameters(p_C_PaySelectionLine_ID)
						.first();
		//
		return existingPaymentRequestLine;
	}

}
