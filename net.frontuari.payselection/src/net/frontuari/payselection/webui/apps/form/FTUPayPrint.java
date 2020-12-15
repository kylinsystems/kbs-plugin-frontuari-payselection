/******************************************************************************
 * Copyright (C) 2009 Low Heng Sin                                            *
 * Copyright (C) 2009 Idalica Corporation                                     *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *                                                                            *
 *  Contributors:                                                             *
 *    Carlos Ruiz - GlobalQSS:                                                *
 *      FR 3132033 - Make payment export class configurable per bank
 *    Markus Bozem:  IDEMPIERE-1546 / IDEMPIERE-3286        				  *
 *****************************************************************************/
package net.frontuari.payselection.webui.apps.form;

import static org.compiere.model.SystemIDs.REFERENCE_PAYMENTRULE;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.base.IPaymentExporterFactory;
import org.adempiere.base.Service;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.acct.Doc;
import org.compiere.apps.form.PayPrint;
import org.compiere.model.MConversionType;
import org.compiere.model.MDepositBatch;
import org.compiere.model.MDepositBatchLine;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MLookupInfo;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionLine;
import org.compiere.model.MPaymentBatch;
import org.compiere.model.X_C_Payment;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Language;
import org.compiere.util.Msg;
import org.compiere.util.PaymentExport;
import org.compiere.util.Trx;
import org.compiere.util.ValueNamePair;

import net.frontuari.payselection.base.FTUForm;
import net.frontuari.payselection.model.FTUMPaySelectionCheck;
import net.frontuari.payselection.model.FTUMPayment;
import net.frontuari.payselection.model.MFTUPaymentRequestLine;

public class FTUPayPrint extends FTUForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6659322715831696429L;
	/** Logger								*/
	static private CLogger	s_log = CLogger.getCLogger (FTUPayPrint.class);

	@Override
	protected void initForm() {
	}
	
	/**	Window No			*/
	public int         	m_WindowNo = 0;
	/**	Used Bank Account	*/
	public int				m_C_BankAccount_ID = -1;
	/**	Export Class for Bank Account	*/
	public String			m_PaymentExportClass = null;
	/**	Payment Selection	*/
	public int         		m_C_PaySelection_ID = 0;

	/** Payment Information */
	public FTUMPaySelectionCheck[]     m_checks = null;
	/** Payment Batch		*/
	public MPaymentBatch	m_batch = null; 
	/**	Logger			*/
	public static final CLogger log = CLogger.getCLogger(PayPrint.class);
	
	public String bank;
	public String currency;
	public BigDecimal balance;
	protected PaymentExport m_PaymentExport;
	
	/**
	 *  PaySelect changed - load Bank
	 */
	public void loadPaySelectInfo(int C_PaySelection_ID)
	{
		//  load Banks from PaySelectLine
		m_C_BankAccount_ID = -1;
		String sql = "SELECT ps.C_BankAccount_ID, b.Name || ' ' || ba.AccountNo,"	//	1..2
			+ " c.ISO_Code, CurrentBalance, ba.PaymentExportClass "					//	3..5
			+ "FROM C_PaySelection ps"
			+ " INNER JOIN C_BankAccount ba ON (ps.C_BankAccount_ID=ba.C_BankAccount_ID)"
			+ " INNER JOIN C_Bank b ON (ba.C_Bank_ID=b.C_Bank_ID)"
			+ " INNER JOIN C_Currency c ON (ba.C_Currency_ID=c.C_Currency_ID) "
			+ "WHERE ps.C_PaySelection_ID=? AND ps.Processed='Y' AND ba.IsActive='Y'";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, C_PaySelection_ID);
			rs = pstmt.executeQuery();
			if (rs.next())
			{
				m_C_BankAccount_ID = rs.getInt(1);
				bank = rs.getString(2);
				currency = rs.getString(3);
				balance = rs.getBigDecimal(4);
				m_PaymentExportClass = rs.getString(5);
			}
			else
			{
				m_C_BankAccount_ID = -1;
				bank = "";
				currency = "";
				balance = Env.ZERO;
				m_PaymentExportClass = null;
				log.log(Level.SEVERE, "No active BankAccount for C_PaySelection_ID=" + C_PaySelection_ID);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
	}   //  loadPaySelectInfo

	/**
	 *  Bank changed - load PaymentRule
	 */
	public ArrayList<ValueNamePair> loadPaymentRule(int C_PaySelection_ID)
	{
		ArrayList<ValueNamePair> data = new ArrayList<ValueNamePair>();

		// load PaymentRule for Bank
		int AD_Reference_ID = REFERENCE_PAYMENTRULE;  //  MLookupInfo.getAD_Reference_ID("All_Payment Rule");
		Language language = Language.getLanguage(Env.getAD_Language(Env.getCtx()));
		MLookupInfo info = MLookupFactory.getLookup_List(language, AD_Reference_ID);
		String sql = info.Query.substring(0, info.Query.indexOf(" ORDER BY"))
			+ " AND " + info.KeyColumn
			+ " IN (SELECT PaymentRule FROM C_PaySelectionCheck WHERE C_PaySelection_ID=?) "
			+ info.Query.substring(info.Query.indexOf(" ORDER BY"));
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, C_PaySelection_ID);
			rs = pstmt.executeQuery();
			//
			while (rs.next())
			{
				ValueNamePair pp = new ValueNamePair(rs.getString(2), rs.getString(3));
				data.add(pp);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		
		if (data.size() == 0)
			if (log.isLoggable(Level.CONFIG)) log.config("PaySel=" + C_PaySelection_ID + ", BAcct=" + m_C_BankAccount_ID + " - " + sql);
		
		return data;
	}   //  loadPaymentRule
	
	public String noPayments;
	public Integer documentNo;
	public Double sumPayments;
	public Integer printFormatId;

	/**
	 *  PaymentRule changed - load DocumentNo, NoPayments,
	 *  enable/disable EFT, Print
	 */
	public String loadPaymentRuleInfo(int C_PaySelection_ID, String PaymentRule)
	{
		String msg = null;
		
		String sql = "SELECT COUNT(*),SUM(payamt) "
			+ "FROM C_PaySelectionCheck "
			+ "WHERE C_PaySelection_ID=? AND PaymentRule=?";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, C_PaySelection_ID);
			pstmt.setString(2, PaymentRule);
			rs = pstmt.executeQuery();
			//
			if (rs.next())
			{
				noPayments = String.valueOf(rs.getInt(1));
				sumPayments = rs.getDouble(2);
			}   
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}

		printFormatId = null;
		documentNo = null;
		
		//  DocumentNo
		sql = "SELECT CurrentNext, Check_PrintFormat_ID "
			+ "FROM C_BankAccountDoc "
			+ "WHERE C_BankAccount_ID=? AND PaymentRule=? AND IsActive='Y'";
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, m_C_BankAccount_ID);
			pstmt.setString(2, PaymentRule);
			rs = pstmt.executeQuery();
			//
			if (rs.next())
			{
				documentNo = Integer.valueOf(rs.getInt(1));
				printFormatId = Integer.valueOf(rs.getInt(2));
			}
			else
			{
				log.log(Level.SEVERE, "VPayPrint.loadPaymentRuleInfo - No active BankAccountDoc for C_BankAccount_ID="
					+ m_C_BankAccount_ID + " AND PaymentRule=" + PaymentRule);
				msg = "VPayPrintNoDoc";
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		
		return msg;
	}   //  loadPaymentRuleInfo
	
	protected int loadPaymentExportClass (StringBuffer err)
	{
		m_PaymentExport = null ;
		
		if (m_PaymentExportClass == null || m_PaymentExportClass.trim().length() == 0) {
			m_PaymentExportClass = "org.compiere.util.GenericPaymentExport";
		}
		try
		{
			List<IPaymentExporterFactory> factories = Service.locator().list(IPaymentExporterFactory.class).getServices();
			if (factories != null && !factories.isEmpty()) {
				for(IPaymentExporterFactory factory : factories) {
					m_PaymentExport = factory.newPaymentExporterInstance(m_PaymentExportClass);
					if (m_PaymentExport != null)
						break;
				}
			}
			
			if (m_PaymentExport == null)
			{
				Class<?> clazz = Class.forName (m_PaymentExportClass);
				m_PaymentExport = (PaymentExport)clazz.getDeclaredConstructor().newInstance();
			}
			
		}
		catch (ClassNotFoundException e)
		{
			if (err!=null)
			{
				err.append("No custom PaymentExport class " + m_PaymentExportClass + " - " + e.toString());
				log.log(Level.SEVERE, err.toString(), e);
			}
			return -1;
		}
		catch (Exception e)
		{
			if (err!=null)
			{
				err.append("Error in " + m_PaymentExportClass + " check log, " + e.toString());
				log.log(Level.SEVERE, err.toString(), e);
			}
			return -1;
		}
		return 0 ;
	} // loadPaymentExportClass

	/**************************************************************************
	 * 	Confirm Print.
	 * 	Create Payments the first time 
	 * 	@param checks checks
	 * 	@param batch batch
	 * 	@param createDeposit create deposit batch
	 * 	@return last Document number or 0 if nothing printed
	 */
	public int confirmPrint (FTUMPaySelectionCheck[] checks, MPaymentBatch batch, boolean createDepositBatch, String trxName)
	{
		Trx trx = null;
		int lastDocumentNo = 0;

		if (checks.length > 0)
		{
			trx = Trx.get(trxName, true);
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
					
					confirmPrint(check, batch, trxName);
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
				trx.rollback();
				trx.close();
				throw new AdempiereException(e);
			} finally {
				trx.commit();
				trx.close();
			}
		}
		if (s_log.isLoggable(Level.FINE)) s_log.fine("Last Document No = " + lastDocumentNo);
		return lastDocumentNo;
	}	//	confirmPrint
	
	/**************************************************************************
	 * 	Confirm Print for a payment selection check
	 * 	Create Payment the first time 
	 * 	@param check check
	 * 	@param batch batch
	 */
	public static void confirmPrint (FTUMPaySelectionCheck check, MPaymentBatch batch, String trxName)
	{
		
		Trx trx = Trx.get(trxName, true);
		trx.setDisplayName(FTUPayPrint.class.getName()+"_confirmPrintExport");
		try {
			FTUMPayment payment = new FTUMPayment(check.getCtx(), check.getC_Payment_ID(), trxName);
			//	Existing Payment
			if (check.getC_Payment_ID() != 0)
			{
				//	Update check number
				if (check.getPaymentRule().equals(FTUMPaySelectionCheck.PAYMENTRULE_Check))
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
				if (check.getPaymentRule().equals(FTUMPaySelectionCheck.PAYMENTRULE_Check))
					payment.setBankCheck (check.getParent().getC_BankAccount_ID(), false, check.getDocumentNo());
				else if (check.getPaymentRule().equals(FTUMPaySelectionCheck.PAYMENTRULE_CreditCard))
					payment.setTenderType(X_C_Payment.TENDERTYPE_CreditCard);
				else if (check.getPaymentRule().equals(FTUMPaySelectionCheck.PAYMENTRULE_DirectDeposit)
					|| check.getPaymentRule().equals(FTUMPaySelectionCheck.PAYMENTRULE_DirectDebit))
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
			trx.commit();
		} catch (Exception e) {
			trx.rollback();
			throw new AdempiereException(e);
		} finally {
			trx.close();
		}
	}	//	confirmPrint
	
}
