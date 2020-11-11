package net.frontuari.payselection.model;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.acct.Doc;
import org.compiere.model.MConversionType;
import org.compiere.model.MDepositBatch;
import org.compiere.model.MDepositBatchLine;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.model.MPaySelectionLine;
import org.compiere.model.MPaymentBatch;
import org.compiere.model.X_C_Payment;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;

public class FTUMPaySelectionCheck extends MPaySelectionCheck {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1683913327811773371L;

	public FTUMPaySelectionCheck(MPaySelection ps, String PaymentRule) {
		super(ps, PaymentRule);
	}
	
	public FTUMPaySelectionCheck(MPaySelectionLine line, String PaymentRule) {
		super(line, PaymentRule);
	}
	
	public FTUMPaySelectionCheck(Properties ctx, int C_PaySelectionCheck_ID, String trxName) {
		super(ctx, C_PaySelectionCheck_ID, trxName);
	}
	
	public FTUMPaySelectionCheck(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	/** Logger								*/
	static private CLogger	s_log = CLogger.getCLogger (FTUMPaySelectionCheck.class);
	
	/**************************************************************************
	 * 	Confirm Print.
	 * 	Create Payments the first time 
	 * 	@param checks checks
	 * 	@param batch batch
	 * 	@return last Document number or 0 if nothing printed
	 */

	public static int confirmPrint (FTUMPaySelectionCheck[] checks, MPaymentBatch batch)
	{
		return confirmPrint (checks,batch,false) ;
	} // confirmPrint
	
	/**************************************************************************
	 * 	Confirm Print for a payment selection check
	 * 	Create Payment the first time 
	 * 	@param check check
	 * 	@param batch batch
	 */
	public static void confirmPrint (FTUMPaySelectionCheck check, MPaymentBatch batch)
	{
		boolean localTrx = false;
		String trxName = check.get_TrxName();
		Trx trx = null;
		if (trxName == null) {
			localTrx = true;
			trxName = Trx.createTrxName("ConfirmPrintSingle");
			trx = Trx.get(trxName, true);
			trx.setDisplayName(FTUMPaySelectionCheck.class.getName()+"_confirmPrint");
			check.set_TrxName(trxName);
		}
		try {
			FTUMPayment payment = new FTUMPayment(check.getCtx(), check.getC_Payment_ID(), trxName);
			//	Existing Payment
			if (check.getC_Payment_ID() != 0)
			{
				//	Update check number
				if (check.getPaymentRule().equals(PAYMENTRULE_Check))
				{
					payment.setCheckNo(check.getDocumentNo());
					payment.saveEx();
				}
			}
			else	//	New Payment
			{
				payment = new FTUMPayment(check.getCtx(), 0, trxName);
				payment.setAD_Org_ID(check.getAD_Org_ID());
				
				MPaySelection ps = check.getParent();
				MDocType dt = new MDocType(Env.getCtx(), ps.get_ValueAsInt("C_DocType_ID"), trxName);
				int C_DocTypePayment_ID = dt.get_ValueAsInt("C_DocTypePayment_ID");
				if(C_DocTypePayment_ID>0)
					payment.setC_DocType_ID(C_DocTypePayment_ID);
				//
				if (check.getPaymentRule().equals(PAYMENTRULE_Check))
					payment.setBankCheck (check.getParent().getC_BankAccount_ID(), false, check.getDocumentNo());
				else if (check.getPaymentRule().equals(PAYMENTRULE_CreditCard))
					payment.setTenderType(X_C_Payment.TENDERTYPE_CreditCard);
				else if (check.getPaymentRule().equals(PAYMENTRULE_DirectDeposit)
					|| check.getPaymentRule().equals(PAYMENTRULE_DirectDebit))
					payment.setBankACH(check);
				else
				{
					s_log.log(Level.SEVERE, "Unsupported Payment Rule=" + check.getPaymentRule());
					return;
				}
				payment.setTrxType(X_C_Payment.TRXTYPE_CreditPayment);
				payment.setAmount(check.getParent().getC_Currency_ID(), check.getPayAmt());
				payment.setDiscountAmt(check.getDiscountAmt());
				payment.setWriteOffAmt(check.getWriteOffAmt());
				payment.setDateTrx(check.getParent().getPayDate());
				payment.setDateAcct(payment.getDateTrx()); // globalqss [ 2030685 ]
				payment.setC_BPartner_ID(check.getC_BPartner_ID());
				payment.setC_ConversionType_ID(MConversionType.getDefault(check.getAD_Client_ID()));
				//	Link to Batch
				if (batch != null)
				{
					if (batch.getC_PaymentBatch_ID() == 0)
						batch.saveEx(trxName);	//	new
					payment.setC_PaymentBatch_ID(batch.getC_PaymentBatch_ID());
				}
				//	Link to Invoice
				MPaySelectionLine[] psls = null;
				if(check.getQty() == 1 || dt.get_ValueAsBoolean("IsManual")) {
					psls = check.getPaySelectionLines(true);
				}				
				if (s_log.isLoggable(Level.FINE)) s_log.fine("confirmPrint - " + check + " (#SelectionLines=" + psls.length + ")");
				
				if (check.getQty() == 1 && psls != null && psls.length == 1)
				{
					MPaySelectionLine psl = psls[0];
					if (s_log.isLoggable(Level.FINE)) s_log.fine("Map to Invoice " + psl);
					//
					if(psl.get_ValueAsInt("C_Order_ID") > 0)
					{
						payment.setC_Order_ID (psl.get_ValueAsInt("C_Order_ID"));
						payment.setIsPrepayment(true);
					}
					else if(psl.getC_Invoice_ID() > 0)
					{
						payment.setC_Invoice_ID (psl.getC_Invoice_ID());
					}
					//	Manual
					else
					{
						MFTUPaymentRequestLine prl = new MFTUPaymentRequestLine(Env.getCtx(), psl.get_ValueAsInt("FTU_PaymentRequestLine_ID") , trxName);
						if(prl.getFTU_PaymentRequest().getC_Charge_ID() > 0)
							payment.setC_Charge_ID(prl.getFTU_PaymentRequest().getC_Charge_ID());
						else
							payment.setIsPrepayment(true);
					}
					payment.setDiscountAmt (psl.getDiscountAmt());
					payment.setWriteOffAmt (psl.getWriteOffAmt());
					BigDecimal overUnder = psl.getOpenAmt().subtract(psl.getPayAmt())
						.subtract(psl.getDiscountAmt()).subtract(psl.getWriteOffAmt()).subtract(psl.getDifferenceAmt());
					payment.setOverUnderAmt(overUnder);
				}
				else
				{
					
					if(dt.get_ValueAsBoolean("IsOrderPrePayment"))
					{
						payment.setIsPrepayment(true);
					}
					else if(dt.get_ValueAsBoolean("IsManual"))
					{
						MPaySelectionLine psl = psls[0];
						MFTUPaymentRequestLine prl = new MFTUPaymentRequestLine(Env.getCtx(), psl.get_ValueAsInt("FTU_PaymentRequestLine_ID") , trxName);
						if(prl.getFTU_PaymentRequest().getC_Charge_ID() > 0)
							payment.setC_Charge_ID(prl.getFTU_PaymentRequest().getC_Charge_ID());
						else
							payment.setIsPrepayment(true);
					}
					payment.setWriteOffAmt(Env.ZERO);
					payment.setDiscountAmt(Env.ZERO);
				}
				payment.saveEx();
				//
				int C_Payment_ID = payment.get_ID();
				if (C_Payment_ID < 1)
					s_log.log(Level.SEVERE, "Payment not created=" + check);
				else
				{
					check.setC_Payment_ID (C_Payment_ID);
					check.saveEx();	//	Payment process needs it
					// added AdempiereException by zuhri
					if (!payment.processIt(DocAction.ACTION_Complete))
						throw new AdempiereException(Msg.getMsg(Env.getCtx(), "FailedProcessingDocument") + " - " + payment.getProcessMsg());
					// end added
					payment.saveEx();
				}
			}	//	new Payment

			check.setIsPrinted(true);
			check.setProcessed(true);
			check.saveEx();
		} catch (Exception e) {
			if (localTrx && trx != null) {
				trx.rollback();
				trx.close();
				trx = null;
			}
			throw new AdempiereException(e);
		} finally {
			if (localTrx && trx != null) {
				trx.commit();
				trx.close();
			}
		}
	}	//	confirmPrint
	
	/**************************************************************************
	 * 	Confirm Print.
	 * 	Create Payments the first time 
	 * 	@param checks checks
	 * 	@param batch batch
	 * 	@param createDeposit create deposit batch
	 * 	@return last Document number or 0 if nothing printed
	 */
	public static int confirmPrint (FTUMPaySelectionCheck[] checks, MPaymentBatch batch, boolean createDepositBatch)
	{
		boolean localTrx = false;
		String trxName = null;
		int lastDocumentNo = 0;

		if (checks.length > 0)
		{
			trxName = checks[0].get_TrxName();
			Properties ctx = checks[0].getCtx();
			int c_BankAccount_ID = checks[0].getC_PaySelection().getC_BankAccount_ID() ;
			String paymentRule = checks[0].getPaymentRule() ;
			Boolean isDebit ;
			if (MInvoice.PAYMENTRULE_DirectDeposit.compareTo(paymentRule) == 0
					|| MInvoice.PAYMENTRULE_Check.compareTo(paymentRule) == 0
					|| MInvoice.PAYMENTRULE_OnCredit.compareTo(paymentRule) == 0)
			{
				isDebit = false ;
			}
			else if (MInvoice.PAYMENTRULE_DirectDebit.compareTo(paymentRule) == 0)
			{
				isDebit = true ;
			}
			else
			{
				isDebit = false ;
				createDepositBatch = false ;
			}
			Trx trx = null;
			if (trxName == null) {
				localTrx = true;
				trxName = Trx.createTrxName("ConfirmPrintMulti");
				trx = Trx.get(trxName, true);
			}

			try {
				MDepositBatch depositBatch = null;
				if (createDepositBatch)
				{
					depositBatch = new MDepositBatch(ctx, 0, trxName) ;
					depositBatch.setC_BankAccount_ID(c_BankAccount_ID);
					if (isDebit)
					{
						depositBatch.setC_DocType_ID(MDocType.getDocType(Doc.DOCTYPE_ARReceipt));
					}
					else
					{
						depositBatch.setC_DocType_ID(MDocType.getDocType(Doc.DOCTYPE_APPayment));
					}
					depositBatch.setDateDeposit(new Timestamp((new Date()).getTime()));
					depositBatch.setDateDoc(new Timestamp((new Date()).getTime()));
					depositBatch.saveEx();
				}

				for (int i = 0; i < checks.length; i++)
				{
					FTUMPaySelectionCheck check = checks[i];
					if (localTrx)
						check.set_TrxName(trxName);
					confirmPrint(check, batch);
					if (createDepositBatch)
					{
						MDepositBatchLine depositBatchLine = new MDepositBatchLine(depositBatch) ;
						depositBatchLine.setC_Payment_ID(check.getC_Payment_ID());
						depositBatchLine.setProcessed(true);
						depositBatchLine.saveEx();
					}
					//	Get Check Document No
					try
					{
						int no = Integer.parseInt(check.getDocumentNo());
						if (lastDocumentNo < no)
							lastDocumentNo = no;
					}
					catch (NumberFormatException ex)
					{
						s_log.log(Level.SEVERE, "DocumentNo=" + check.getDocumentNo(), ex);
					}
				}	//	all checks

				if (createDepositBatch)
				{

					depositBatch.setProcessed(true);
					depositBatch.saveEx();
				}

			} catch (Exception e) {
				if (localTrx && trx != null) {
					trx.rollback();
					trx.close();
					trx = null;
				}
				throw new AdempiereException(e);
			} finally {
				if (localTrx && trx != null) {
					trx.commit();
					trx.close();
				}
			}
		}
		if (s_log.isLoggable(Level.FINE)) s_log.fine("Last Document No = " + lastDocumentNo);
		return lastDocumentNo;
	}	//	confirmPrint

	/**************************************************************************
	 *  Get Checks of Payment Selection without check no assignment
	 *
	 *  @param C_PaySelection_ID Payment Selection
	 *  @param PaymentRule Payment Rule
	 *	@param trxName transaction
	 *  @return array of checks
	 */
	public static FTUMPaySelectionCheck[] get (int C_PaySelection_ID, String PaymentRule, String trxName)
	{
		if (s_log.isLoggable(Level.FINE)) s_log.fine("C_PaySelection_ID=" + C_PaySelection_ID
			+ ", PaymentRule=" +  PaymentRule);
		ArrayList<FTUMPaySelectionCheck> list = new ArrayList<FTUMPaySelectionCheck>();

		String sql = "SELECT * FROM C_PaySelectionCheck "
			+ "WHERE C_PaySelection_ID=? AND PaymentRule=? "
			+ "ORDER BY C_PaySelectionCheck_ID"; // order by the C_PaySelectionCheck_ID
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, trxName);
			pstmt.setInt(1, C_PaySelection_ID);
			pstmt.setString(2, PaymentRule);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				FTUMPaySelectionCheck check = new FTUMPaySelectionCheck (Env.getCtx(), rs, trxName);
				list.add(check);
			}
		}
		catch (SQLException e)
		{
			s_log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		//  convert to Array
		FTUMPaySelectionCheck[] retValue = new FTUMPaySelectionCheck[list.size()];
		list.toArray(retValue);
		return retValue;
	}   //  get
	
}
