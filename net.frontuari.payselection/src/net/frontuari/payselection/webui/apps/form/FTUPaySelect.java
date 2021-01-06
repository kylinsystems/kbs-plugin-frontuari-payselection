/******************************************************************************
 * Copyright (C) 2009 Low Heng Sin                                            *
 * Copyright (C) 2009 Idalica Corporation                                     *
 * Copyright (C) 2020 Jorge Colmenarez aka Frontuari, C.A.                    *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/
package net.frontuari.payselection.webui.apps.form;

import static org.compiere.model.SystemIDs.REFERENCE_PAYMENTRULE;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.DBException;
import org.compiere.minigrid.ColumnInfo;
import org.compiere.minigrid.IDColumn;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.MBankAccount;
import org.compiere.model.MDocType;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MLookupInfo;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionLine;
import org.compiere.model.MRole;
import org.compiere.model.MSequence;
import org.compiere.model.X_C_Order;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Language;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.ValueNamePair;

import net.frontuari.payselection.base.FTUForm;
import net.frontuari.payselection.model.MFTUPaymentRequestLine;

public class FTUPaySelect extends FTUForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = 368035202498536430L;

	@Override
	protected void initForm() {
	}
	
	/** @todo withholding */

	/**	Window No			*/
	public int         	m_WindowNo = 0;

	/** Format                  */
	public DecimalFormat   m_format = DisplayType.getNumberFormat(DisplayType.Amount);
	/** Bank Balance            */
	private BigDecimal      m_bankBalance = Env.ZERO;
	/** SQL for Query           */
	private String          m_sql;
	/** Number of selected rows */
	public int             m_noSelected = 0;
	/** Client ID               */
	private int             m_AD_Client_ID = 0;
	/**/
	public boolean         m_isLocked = false;
	/** Payment Selection		*/
	public MPaySelection	m_ps = null;
	/** one-To-one payment per invoice */
	public boolean			m_isOnePaymentPerInvoice	= false;
	/**	Logger			*/
	public static final CLogger log = CLogger.getCLogger(FTUPaySelect.class);

	public ArrayList<BankInfo> getBankAccountData()
	{
		ArrayList<BankInfo> data = new ArrayList<BankInfo>();
		//
		m_AD_Client_ID = Env.getAD_Client_ID(Env.getCtx());
		//  Bank Account Info
		String sql = MRole.getDefault().addAccessSQL(
			"SELECT ba.C_BankAccount_ID,"                       //  1
			+ "b.Name || ' ' || ba.AccountNo AS Name,"          //  2
			+ "ba.C_Currency_ID, c.ISO_Code,"                   //  3..4
			+ "ba.CurrentBalance "                              //  5
			+ "FROM C_Bank b, C_BankAccount ba, C_Currency c "
			+ "WHERE b.C_Bank_ID=ba.C_Bank_ID"
			+ " AND ba.C_Currency_ID=c.C_Currency_ID AND ba.IsActive='Y' "
			+ " AND EXISTS (SELECT * FROM C_BankAccountDoc d WHERE d.C_BankAccount_ID=ba.C_BankAccount_ID AND d.IsActive='Y' ) "
			+ "ORDER BY 2",
			"b", MRole.SQL_FULLYQUALIFIED, MRole.SQL_RW);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				boolean transfers = false;
				BankInfo bi = new BankInfo (rs.getInt(1), rs.getInt(3),
					rs.getString(2), rs.getString(4),
					rs.getBigDecimal(5), transfers);
				data.add(bi);
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
		
		return data;
	}
	
	public ArrayList<KeyNamePair> getBPartnerData()
	{
		ArrayList<KeyNamePair> data = new ArrayList<KeyNamePair>();
		
		//  Optional BusinessPartner with unpaid AP Invoices
		KeyNamePair pp = new KeyNamePair(0, "");
		data.add(pp);
		
		String sql = MRole.getDefault().addAccessSQL(
			"SELECT bp.C_BPartner_ID, bp.Name FROM C_BPartner bp", "bp",
			MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO)
			+ " AND (EXISTS (SELECT 1 FROM C_Invoice i WHERE bp.C_BPartner_ID=i.C_BPartner_ID"
			//	X_C_Order.PAYMENTRULE_DirectDebit
			  + " AND (i.IsSOTrx='N' OR (i.IsSOTrx='Y' AND i.PaymentRule='D'))"
			  + " AND i.IsPaid<>'Y') "
			+ " OR EXISTS (SELECT 1 FROM C_Order o WHERE bp.C_BPartner_ID=o.C_BPartner_ID "
			+ " AND o.IsSOTrx ='N' AND NOT EXISTS (SELECT 1 FROM C_Invoice inv WHERE inv.C_Order_ID = o.C_Order_ID)))"
			+ "ORDER BY 2";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				pp = new KeyNamePair(rs.getInt(1), rs.getString(2));
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
		
		return data;
	}
	/// this is a fucking method for get PaymentRequest
	/** Depreciated Method**/
	public ArrayList<KeyNamePair> getDocTypeData()
	{
		ArrayList<KeyNamePair> data = new ArrayList<KeyNamePair>();
		String sql = null;
		/**Document type**/
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			sql = MRole.getDefault().addAccessSQL(
				"SELECT DISTINCT pr.FTU_PaymentRequest_ID,pr.DocumentNo "
				+ "FROM FTU_PaymentRequest pr "
				+ "JOIN FTU_PaymentRequestLine prl ON (pr.FTU_PaymentRequest_ID = prl.FTU_PaymentRequest_ID) "
				+ "WHERE pr.AD_Client_ID = ? "
				+ "AND pr.DocStatus = 'CO' "
				+ "AND NOT EXISTS (SELECT 1 FROM C_PaySelectionLine psl JOIN C_PaySelection ps ON psl.C_PaySelection_ID = ps.C_PaySelection_ID "
				+ " AND ps.IsActive ='Y' WHERE psl.IsActive = 'Y' AND psl.FTU_PaymentRequestLine_ID = prl.FTU_PaymentRequestLine_ID) "
				+ "ORDER BY 2", "pr",
				MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO);

			KeyNamePair dt = new KeyNamePair(0, "");
			data.add(dt);
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, m_AD_Client_ID);		//	Client
			rs = pstmt.executeQuery();

			while (rs.next())
			{
				dt = new KeyNamePair(rs.getInt(1), rs.getString(2));
				data.add(dt);
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
		
		return data;
	}
	
	/**
	 * Get Organization for PaySelection
	 * @return ArrayList
	 */
	public ArrayList<KeyNamePair> getOrganization()
	{
		ArrayList<KeyNamePair> data = new ArrayList<KeyNamePair>();
		String sql = null;
		/**Document type**/
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			sql = MRole.getDefault().addAccessSQL(
				"SELECT o.AD_Org_ID,o.Name FROM AD_Org o WHERE o.IsSummary = 'N' AND o.IsActive = 'Y' ORDER BY o.Value", "o",
				MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO);

			KeyNamePair dt = new KeyNamePair(0, "");
			data.add(dt);
			pstmt = DB.prepareStatement(sql, null);
			rs = pstmt.executeQuery();

			while (rs.next())
			{
				dt = new KeyNamePair(rs.getInt(1), rs.getString(2));
				data.add(dt);
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
		
		return data;
	}
	
	/**
	 * Get Document Type Target for PaySelection
	 * @return ArrayList
	 */
	public ArrayList<KeyNamePair> getDocTypeTargetData()
	{
		ArrayList<KeyNamePair> data = new ArrayList<KeyNamePair>();
		String sql = null;
		/**Document type**/
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			sql = MRole.getDefault().addAccessSQL(
				"SELECT doc.c_doctype_id,doc.name FROM c_doctype doc WHERE doc.ad_client_id = ? AND doc.docbasetype in ('PRQ') ORDER BY doc.isdefault DESC,doc.name ASC, doc.isorderprepayment ASC", "doc",
				MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO);

			KeyNamePair dt = null;
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, m_AD_Client_ID);		//	Client
			rs = pstmt.executeQuery();

			while (rs.next())
			{
				dt = new KeyNamePair(rs.getInt(1), rs.getString(2));
				data.add(dt);
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
		
		return data;
	}
	
	public void prepareTable(IMiniTable miniTable,boolean isPrePayment,boolean isManual,boolean isDividendPayment)
	{
		Properties ctx = Env.getCtx();
		/**  prepare MiniTable
		 *
		SELECT i.C_Invoice_ID, i.DateInvoiced+p.NetDays AS DateDue,
		bp.Name, i.DocumentNo, c.ISO_Code, i.GrandTotal,
		paymentTermDiscount(i.GrandTotal, i.C_PaymentTerm_ID, i.DateInvoiced, SysDate) AS Discount,
		SysDate-paymentTermDueDays(i.C_PaymentTerm_ID,i.DateInvoiced) AS DiscountDate,
		i.GrandTotal-paymentTermDiscount(i.GrandTotal,i.C_PaymentTerm_ID,i.DateInvoiced,SysDate) AS DueAmount,
		currencyConvert(i.GrandTotal-paymentTermDiscount(i.GrandTotal,i.C_PaymentTerm_ID,i.DateInvoiced,SysDate,null),
			i.C_Currency_ID,xx100,SysDate) AS PayAmt
		FROM C_Invoice_v i, C_BPartner bp, C_Currency c, C_PaymentTerm p
		WHERE i.IsSOTrx='N'
		AND i.C_BPartner_ID=bp.C_BPartner_ID
		AND i.C_Currency_ID=c.C_Currency_ID
		AND i.C_PaymentTerm_ID=p.C_PaymentTerm_ID
		AND i.DocStatus IN ('CO','CL')
		ORDER BY 2,3
		 */

		if(!isPrePayment && !isManual && !isDividendPayment)
		{
			m_sql = miniTable.prepareTable(new ColumnInfo[] {
					//  0..8
					new ColumnInfo(" ", "i.C_Invoice_ID", IDColumn.class, false, false, null),
					new ColumnInfo(Msg.translate(ctx, "AD_Org_ID"), "o.Name", String.class),
					new ColumnInfo(Msg.translate(ctx, "C_DocType_ID"), "prdt.Name", String.class),
					new ColumnInfo(Msg.translate(ctx, "FTU_PaymentRequest_ID"), "pr.DocumentNo", KeyNamePair.class,true,false,"prl.FTU_PaymentRequestLine_ID"),
					new ColumnInfo(Msg.translate(ctx, "DueDate"), "prl.DueDate AS DateDue", Timestamp.class, true, true, null),
					new ColumnInfo(Msg.translate(ctx, "C_BPartner_ID"), "bp.Name", KeyNamePair.class, true, false, "i.C_BPartner_ID"),
					new ColumnInfo(Msg.translate(ctx, "C_DocTypeTarget_ID"), "dt.Name", KeyNamePair.class, true, false, "i.C_DocType_ID"),
					new ColumnInfo(Msg.translate(ctx, "DocumentNo"), "i.DocumentNo", String.class),
					new ColumnInfo(Msg.translate(ctx, "C_Currency_ID"), "c.ISO_Code", KeyNamePair.class, true, false, "i.C_Currency_ID"),
					// 9..12
					new ColumnInfo(Msg.translate(ctx, "GrandTotal"), "i.GrandTotal", BigDecimal.class),
					/*new ColumnInfo(Msg.translate(ctx, "DiscountAmt"), "currencyConvert(invoiceDiscount(i.C_Invoice_ID,?,i.C_InvoicePaySchedule_ID),i.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID)", BigDecimal.class),
					new ColumnInfo(Msg.translate(ctx, "WriteOffAmt"), "currencyConvert(invoiceWriteOff(i.C_Invoice_ID),i.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID)", BigDecimal.class),
					new ColumnInfo(Msg.getMsg(ctx, "DiscountDate"), "COALESCE((SELECT discountdate from C_InvoicePaySchedule ips WHERE ips.C_InvoicePaySchedule_ID=i.C_InvoicePaySchedule_ID),i.DateInvoiced+p.DiscountDays+p.GraceDays) AS DiscountDate", Timestamp.class),
					*/
					new ColumnInfo(Msg.translate(ctx, "C_CurrencyTo_ID"), "prc.ISO_Code", KeyNamePair.class, true, false, "pr.C_Currency_ID"),
					new ColumnInfo(Msg.getMsg(ctx, "AmountDue"), "currencyConvert(prl.PayAmt-COALESCE(psl.PayAmt,0),pr.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountDue", BigDecimal.class),
					new ColumnInfo(Msg.getMsg(ctx, "AmountPay"), "currencyConvert(prl.PayAmt-COALESCE(psl.PayAmt,0),pr.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountPay", BigDecimal.class,false),
					new ColumnInfo(Msg.translate(ctx, "C_Bank_ID"), "b.Name", String.class),
					new ColumnInfo(Msg.translate(ctx, "AccountNo"), "bpba.AccountNo", String.class)
					},
					//	FROM
					"FTU_PaymentRequest pr"
					+ " INNER JOIN FTU_PaymentRequestLine prl ON (prl.FTU_PaymentRequest_ID=pr.FTU_PaymentRequest_ID)"
					+ " INNER JOIN C_Invoice_v i ON (i.C_Invoice_ID=prl.C_Invoice_ID AND prl.DueDate = i.DueDate)"
					+ " INNER JOIN C_DocType prdt ON (pr.C_DocType_ID=prdt.C_DocType_ID)"
					+ " INNER JOIN C_Currency prc ON (pr.C_Currency_ID=prc.C_Currency_ID)"
					+ " INNER JOIN AD_Org o ON (i.AD_Org_ID=o.AD_Org_ID)"
					+ " INNER JOIN C_BPartner bp ON (i.C_BPartner_ID=bp.C_BPartner_ID)"
					+ " INNER JOIN C_BP_BankAccount bpba ON (prl.C_BP_BankAccount_ID=bpba.C_BP_BankAccount_ID)"
					+ " INNER JOIN C_Bank b ON (bpba.C_Bank_ID=b.C_Bank_ID)"
					+ " INNER JOIN C_DocType dt ON (i.C_DocType_ID=dt.C_DocType_ID)"
					+ " INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID)"
					+ " INNER JOIN C_PaymentTerm p ON (i.C_PaymentTerm_ID=p.C_PaymentTerm_ID)"
					+ " LEFT JOIN (SELECT psl.FTU_PaymentRequestLine_ID,"
					+ "	SUM(currencyConvert(psl.PayAmt,cb.C_Currency_ID,prf.c_currency_id,ps.PayDate,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM C_PaySelectionLine psl "
					+ " INNER JOIN FTU_PaymentRequestLine prlf ON prlf.ftu_paymentrequestline_id = psl.ftu_paymentrequestline_id"
					+ " INNER JOIN FTU_PaymentRequest prf ON  prlf.ftu_paymentrequest_id = prf.ftu_paymentrequest_id"
					+ "	INNER JOIN C_PaySelection ps on psl.C_PaySelection_ID = ps.C_PaySelection_ID "
					+ "	INNER JOIN C_BankAccount cb on ps.C_BankAccount_ID = cb.C_BankAccount_ID "
					+ " INNER JOIN C_Invoice i ON psl.C_Invoice_ID = i.C_Invoice_ID "
					+ " LEFT JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID) "  
					+ " WHERE psl.IsActive='Y' "
					+ " GROUP BY psl.FTU_PaymentRequestLine_ID) psl ON (prl.FTU_PaymentRequestLine_ID=psl.FTU_PaymentRequestLine_ID) "
					/*+ " LEFT JOIN (SELECT psl.C_Invoice_ID,"
					+ "	SUM(currencyConvert(psl.PayAmt,cb.C_Currency_ID,i.C_Currency_ID,ps.PayDate,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM C_PaySelectionLine psl "
					+ "	INNER JOIN C_PaySelection ps on psl.C_PaySelection_ID = ps.C_PaySelection_ID "
					+ "	INNER JOIN C_BankAccount cb on ps.C_BankAccount_ID = cb.C_BankAccount_ID "
					+ " INNER JOIN C_Invoice i ON psl.C_Invoice_ID = i.C_Invoice_ID "
					+ " INNER JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID AND psc.C_Payment_ID IS NULL) "  
					+ " WHERE psl.IsActive='Y' "
					+ " GROUP BY psl.C_Invoice_ID) psl ON (i.C_Invoice_ID=psl.C_Invoice_ID) "*/,
					//	WHERE
					"i.IsSOTrx=? AND IsPaid='N'"
					+ " AND (prl.PayAmt-COALESCE(psl.PayAmt,0)) > 0" //Check that AmountDue <> 0
					+ " AND i.DocStatus IN ('CO','CL') AND pr.DocStatus = 'CO'",	//	additional where & order in loadTableInfo()
					true, "pr");
		}
		else if(isPrePayment && !isManual)
		{
			m_sql = miniTable.prepareTable(new ColumnInfo[] {
					//  0..6
					new ColumnInfo(" ", "i.C_Order_ID", IDColumn.class, false, false, null),
					new ColumnInfo(Msg.translate(ctx, "AD_Org_ID"), "o.Name", String.class),
					new ColumnInfo(Msg.translate(ctx, "C_DocType_ID"), "prdt.Name", String.class),
					new ColumnInfo(Msg.translate(ctx, "FTU_PaymentRequest_ID"), "pr.DocumentNo", KeyNamePair.class,true,false,"prl.FTU_PaymentRequestLine_ID"),
					new ColumnInfo(Msg.translate(ctx, "DueDate"), "prl.DueDate AS DateDue", Timestamp.class, true, true, null),
					new ColumnInfo(Msg.translate(ctx, "C_BPartner_ID"), "bp.Name", KeyNamePair.class, true, false, "i.C_BPartner_ID"),
					new ColumnInfo(Msg.translate(ctx, "C_DocTypeTarget_ID"), "dt.Name", KeyNamePair.class, true, false, "i.C_DocType_ID"),
					new ColumnInfo(Msg.translate(ctx, "DocumentNo"), "i.DocumentNo", String.class),
					new ColumnInfo(Msg.translate(ctx, "C_Currency_ID"), "c.ISO_Code", KeyNamePair.class, true, false, "i.C_Currency_ID"),
					// 7..12
					new ColumnInfo(Msg.translate(ctx, "GrandTotal"), "i.GrandTotal", BigDecimal.class),
					/*new ColumnInfo(Msg.translate(ctx, "DiscountAmt"), "currencyConvert(invoiceDiscount(i.C_Invoice_ID,?,i.C_InvoicePaySchedule_ID),i.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID)", BigDecimal.class),
					new ColumnInfo(Msg.translate(ctx, "WriteOffAmt"), "currencyConvert(invoiceWriteOff(i.C_Invoice_ID),i.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID)", BigDecimal.class),
					new ColumnInfo(Msg.getMsg(ctx, "DiscountDate"), "COALESCE((SELECT discountdate from C_InvoicePaySchedule ips WHERE ips.C_InvoicePaySchedule_ID=i.C_InvoicePaySchedule_ID),i.DateInvoiced+p.DiscountDays+p.GraceDays) AS DiscountDate", Timestamp.class),
					*/
					new ColumnInfo(Msg.translate(ctx, "C_CurrencyTo_ID"), "prc.ISO_Code", KeyNamePair.class, true, false, "pr.C_Currency_ID"),
					new ColumnInfo(Msg.getMsg(ctx, "AmountDue"), "currencyConvert(prl.PayAmt-COALESCE(psl.PayAmt,0),pr.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountDue", BigDecimal.class),
					new ColumnInfo(Msg.getMsg(ctx, "AmountPay"), "currencyConvert(prl.PayAmt-COALESCE(psl.PayAmt,0),pr.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID) AS AmountPay", BigDecimal.class,false),
					new ColumnInfo(Msg.translate(ctx, "C_Bank_ID"), "b.Name", String.class),
					new ColumnInfo(Msg.translate(ctx, "AccountNo"), "bpba.AccountNo", String.class) 
					},
					//	FROM
					"FTU_PaymentRequest pr "
					+ " INNER JOIN FTU_PaymentRequestLine prl ON (prl.FTU_PaymentRequest_ID=pr.FTU_PaymentRequest_ID)"
					+ " INNER JOIN FTU_Order_v i ON (i.C_Order_ID=prl.C_Order_ID AND prl.DueDate = i.DueDate)"
					+ " INNER JOIN C_DocType prdt ON (pr.C_DocType_ID=prdt.C_DocType_ID)"
					+ " INNER JOIN C_Currency prc ON (pr.C_Currency_ID=prc.C_Currency_ID)"
					+ " INNER JOIN AD_Org o ON (i.AD_Org_ID=o.AD_Org_ID)"
					+ " INNER JOIN C_BPartner bp ON (i.C_BPartner_ID=bp.C_BPartner_ID)"
					+ " INNER JOIN C_BP_BankAccount bpba ON (prl.C_BP_BankAccount_ID=bpba.C_BP_BankAccount_ID)"
					+ " INNER JOIN C_Bank b ON (bpba.C_Bank_ID=b.C_Bank_ID)"
					+ " INNER JOIN C_DocType dt ON (i.C_DocType_ID=dt.C_DocType_ID)"
					+ " INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID)"
					+ " INNER JOIN C_PaymentTerm p ON (i.C_PaymentTerm_ID=p.C_PaymentTerm_ID)"
					+ " LEFT JOIN (SELECT psl.FTU_PaymentRequestLine_ID,"
					+ "	SUM(currencyConvert(psl.PayAmt,cb.C_Currency_ID,prf.c_currency_id,ps.PayDate,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM C_PaySelectionLine psl "
					+ " INNER JOIN FTU_PaymentRequestLine prlf ON prlf.ftu_paymentrequestline_id = psl.ftu_paymentrequestline_id"
					+ " INNER JOIN FTU_PaymentRequest prf ON  prlf.ftu_paymentrequest_id = prf.ftu_paymentrequest_id"
					+ "	INNER JOIN C_PaySelection ps on psl.C_PaySelection_ID = ps.C_PaySelection_ID "
					+ "	INNER JOIN C_BankAccount cb on ps.C_BankAccount_ID = cb.C_BankAccount_ID "
					+ " INNER JOIN C_Order i ON psl.C_Order_ID = i.C_Order_ID "
					+ " LEFT JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID) "  
					+ " WHERE psl.IsActive='Y' "
					+ " GROUP BY psl.FTU_PaymentRequestLine_ID) psl ON (prl.FTU_PaymentRequestLine_ID=psl.FTU_PaymentRequestLine_ID) "
					/*+ " LEFT JOIN (SELECT psl.C_Order_ID,"
					+ "	SUM(currencyConvert(psl.PayAmt,cb.C_Currency_ID,i.C_Currency_ID,ps.PayDate,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)) AS PayAmt "
					+ "	FROM C_PaySelectionLine psl "
					+ "	INNER JOIN C_PaySelection ps on psl.C_PaySelection_ID = ps.C_PaySelection_ID "
					+ "	INNER JOIN C_BankAccount cb on ps.C_BankAccount_ID = cb.C_BankAccount_ID "
					+ " INNER JOIN C_Order i ON psl.C_Order_ID = i.C_Order_ID "  
					+ " INNER JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID AND psc.C_Payment_ID IS NULL) "   
					+ " WHERE psl.IsActive='Y' "
					+ " GROUP BY psl.C_Order_ID) psl ON (i.C_Order_ID=psl.C_Order_ID) "*/,
					//	WHERE
					"i.IsSOTrx=? "
					+ " AND i.C_Invoice_ID IS NULL "
					+ " AND (prl.PayAmt-COALESCE(psl.PayAmt,0)) > 0" //Check that AmountDue <> 0
					+ " AND i.DocStatus IN ('CO') AND pr.DocStatus = 'CO'",	//	additional where & order in loadTableInfo()
					true, "pr");
		}
		else if(isManual)
		{
			m_sql = miniTable.prepareTable(new ColumnInfo[] {
					//  0..6
					new ColumnInfo(" ", "prl.C_BPartner_ID", IDColumn.class, false, false, null),
					new ColumnInfo(Msg.translate(ctx, "AD_Org_ID"), "o.Name", String.class),
					new ColumnInfo(Msg.translate(ctx, "C_DocType_ID"), "prdt.Name", String.class),
					new ColumnInfo(Msg.translate(ctx, "FTU_PaymentRequest_ID"), "pr.DocumentNo", KeyNamePair.class,true,false,"prl.FTU_PaymentRequestLine_ID"),
					new ColumnInfo(Msg.translate(ctx, "DueDate"), "prl.DueDate AS DateDue", Timestamp.class, true, true, null),
					new ColumnInfo(Msg.translate(ctx, "C_BPartner_ID"), "bp.Name", KeyNamePair.class, true, false, "prl.C_BPartner_ID"),
					new ColumnInfo(Msg.translate(ctx, "C_DocTypeTarget_ID"), "NULL", KeyNamePair.class, true, false, "0"),
					new ColumnInfo(Msg.translate(ctx, "DocumentNo"), "NULL", String.class),
					new ColumnInfo(Msg.translate(ctx, "C_Currency_ID"), "prc.ISO_Code", KeyNamePair.class, true, false, "pr.C_Currency_ID"),
					// 7..12
					new ColumnInfo(Msg.translate(ctx, "GrandTotal"), "prl.PayAmt", BigDecimal.class),
					/*new ColumnInfo(Msg.translate(ctx, "DiscountAmt"), "currencyConvert(invoiceDiscount(i.C_Invoice_ID,?,i.C_InvoicePaySchedule_ID),i.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID)", BigDecimal.class),
					new ColumnInfo(Msg.translate(ctx, "WriteOffAmt"), "currencyConvert(invoiceWriteOff(i.C_Invoice_ID),i.C_Currency_ID, ?,?,i.C_ConversionType_ID, i.AD_Client_ID,i.AD_Org_ID)", BigDecimal.class),
					new ColumnInfo(Msg.getMsg(ctx, "DiscountDate"), "COALESCE((SELECT discountdate from C_InvoicePaySchedule ips WHERE ips.C_InvoicePaySchedule_ID=i.C_InvoicePaySchedule_ID),i.DateInvoiced+p.DiscountDays+p.GraceDays) AS DiscountDate", Timestamp.class),
					*/
					new ColumnInfo(Msg.translate(ctx, "C_CurrencyTo_ID"), "prc.ISO_Code", KeyNamePair.class, true, false, "pr.C_Currency_ID"),
					new ColumnInfo(Msg.getMsg(ctx, "AmountDue"), "currencyConvert(prl.PayAmt-COALESCE(psl.PayAmt,0),pr.C_Currency_ID, ?,?,pr.C_ConversionType_ID, pr.AD_Client_ID,pr.AD_Org_ID) AS AmountDue", BigDecimal.class),
					new ColumnInfo(Msg.getMsg(ctx, "AmountPay"), "currencyConvert(prl.PayAmt-COALESCE(psl.PayAmt,0),pr.C_Currency_ID, ?,?,pr.C_ConversionType_ID, pr.AD_Client_ID,pr.AD_Org_ID) AS AmountPay", BigDecimal.class,false),
					new ColumnInfo(Msg.translate(ctx, "C_Bank_ID"), "b.Name", String.class),
					new ColumnInfo(Msg.translate(ctx, "AccountNo"), "bpba.AccountNo", String.class) 
					},
					//	FROM
					"FTU_PaymentRequest pr "
					+ " INNER JOIN FTU_PaymentRequestLine prl ON (prl.FTU_PaymentRequest_ID=pr.FTU_PaymentRequest_ID)"
					+ " INNER JOIN C_DocType prdt ON (pr.C_DocType_ID=prdt.C_DocType_ID)"
					+ " INNER JOIN C_Currency prc ON (pr.C_Currency_ID=prc.C_Currency_ID)"
					+ " INNER JOIN AD_Org o ON (pr.AD_Org_ID=o.AD_Org_ID)"
					+ " INNER JOIN C_BPartner bp ON (prl.C_BPartner_ID=bp.C_BPartner_ID)"
					+ " INNER JOIN C_BP_BankAccount bpba ON (prl.C_BP_BankAccount_ID=bpba.C_BP_BankAccount_ID)"
					+ " INNER JOIN C_Bank b ON (bpba.C_Bank_ID=b.C_Bank_ID)"
					+ " LEFT JOIN (SELECT psl.FTU_PaymentRequestLine_ID,"
					+ "	SUM(currencyConvert(psl.PayAmt,cb.C_Currency_ID,prf.c_currency_id,ps.PayDate,prf.C_ConversionType_ID,prf.AD_Client_ID,prf.AD_Org_ID)) AS PayAmt "
					+ "	FROM C_PaySelectionLine psl "
					+ " INNER JOIN FTU_PaymentRequestLine prlf ON prlf.ftu_paymentrequestline_id = psl.ftu_paymentrequestline_id"
					+ " INNER JOIN FTU_PaymentRequest prf ON  prlf.ftu_paymentrequest_id = prf.ftu_paymentrequest_id"
					+ "	INNER JOIN C_PaySelection ps on psl.C_PaySelection_ID = ps.C_PaySelection_ID "
					+ "	INNER JOIN C_BankAccount cb on ps.C_BankAccount_ID = cb.C_BankAccount_ID "
					//+ " INNER JOIN C_Invoice i ON psl.C_Invoice_ID = i.C_Invoice_ID "
					+ " LEFT JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID) "  
					+ " WHERE psl.IsActive='Y' "
					+ " GROUP BY psl.FTU_PaymentRequestLine_ID) psl ON (prl.FTU_PaymentRequestLine_ID=psl.FTU_PaymentRequestLine_ID) "
					/*+ " LEFT JOIN (SELECT psl.FTU_PaymentRequestLine_ID,"
					+ "	SUM(currencyConvert(psl.PayAmt,cb.C_Currency_ID,pr.C_Currency_ID,ps.PayDate,null,psl.AD_Client_ID,psl.AD_Org_ID)) AS PayAmt "
					+ "	FROM C_PaySelectionLine psl "
					+ "	INNER JOIN C_PaySelection ps on psl.C_PaySelection_ID = ps.C_PaySelection_ID "
					+ "	INNER JOIN C_BankAccount cb on ps.C_BankAccount_ID = cb.C_BankAccount_ID "
					+ "	INNER JOIN FTU_PaymentRequestLine prl on psl.FTU_PaymentRequestLine_ID = prl.FTU_PaymentRequestLine_ID "
					+ "	INNER JOIN FTU_PaymentRequest pr on prl.FTU_PaymentRequest_ID = pr.FTU_PaymentRequest_ID "
					+ " INNER JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID AND psc.C_Payment_ID IS NULL) "   
					+ " WHERE psl.IsActive='Y' "
					+ " GROUP BY psl.FTU_PaymentRequestLine_ID) psl ON (prl.FTU_PaymentRequestLine_ID=psl.FTU_PaymentRequestLine_ID) "*/,
					//	WHERE
					"(prl.PayAmt-COALESCE(psl.PayAmt,0)) > 0" //Check that AmountDue <> 0
					+ " AND pr.DocStatus IN ('CO') AND pr.DocStatus = 'CO' AND pr.RequestType IN ('PRM','GLJ')",	//	additional where & order in loadTableInfo()
					true, "pr");
		}
		//Add Support for Generate PaySelection with Type Request Dividend Payment by Argenis Rodríguez 15-12-2020
		else if (isDividendPayment)
		{
			m_sql = miniTable.prepareTable(new ColumnInfo[] {
					new ColumnInfo(" ", "arl.COP_AssemblyRecordLine_ID", IDColumn.class) //0
					, new ColumnInfo(Msg.translate(Env.getCtx(), "AD_Org_ID"), "o.Name", String.class) //1
					, new ColumnInfo(Msg.translate(Env.getCtx(), "C_DocType_ID"), "dt.Name", String.class) //2
					, new ColumnInfo(Msg.translate(Env.getCtx(), "FTU_PaymentRequest_ID"), "pr.DocumentNo", KeyNamePair.class, true, false, "prl.FTU_PaymentRequestLine_ID") //3
					, new ColumnInfo(Msg.translate(Env.getCtx(), "DueDate"), "prl.DueDate AS DateDue", Timestamp.class, true, true, null) //4
					, new ColumnInfo(Msg.translate(Env.getCtx(), "C_BPartner_ID"), "bp.Name", KeyNamePair.class, true, false, "bp.C_BPartner_ID") //5
					, new ColumnInfo(Msg.translate(Env.getCtx(), "DocumentNo"), "ar.ValueNumber AS DocumentNo", String.class) //6
					, new ColumnInfo(Msg.translate(Env.getCtx(), "C_Currency_ID"), "c.ISO_Code", KeyNamePair.class, true, false, "c.C_Currency_ID") //7
					, new ColumnInfo(Msg.translate(Env.getCtx(), "PayAmt"), "arl.PayAmt AS GrandTotal", BigDecimal.class) //8
					, new ColumnInfo(Msg.translate(Env.getCtx(), "C_CurrencyTo_ID"), "cc.ISO_Code", KeyNamePair.class, true, false, "cc.C_Currency_ID") //9
					, new ColumnInfo(Msg.translate(Env.getCtx(), "AmountDue"), "COALESCE(currencyconvert(prl.PayAmt - COALESCE(psl.PayAmt, 0), pr.C_Currency_ID, ?, ?, ar.C_ConversionType_ID, ar.AD_Client_ID, ar.AD_Org_ID), 0) AS AmountDue", BigDecimal.class) //10
					, new ColumnInfo(Msg.translate(Env.getCtx(), "AmountPay"), "COALESCE(currencyconvert(prl.PayAmt - COALESCE(psl.PayAmt, 0), pr.C_Currency_ID, ?, ?, ar.C_ConversionType_ID, ar.AD_Client_ID, ar.AD_Org_ID), 0) AS AmountPay", BigDecimal.class, false) //11
					, new ColumnInfo(Msg.translate(Env.getCtx(), "C_Bank_ID"), "bank.Name", String.class) //12
					, new ColumnInfo(Msg.translate(Env.getCtx(), "AccountNo"), "bpa.AccountNo", String.class) //13
			},
			"FTU_PaymentRequest pr"
			+ " INNER JOIN FTU_PaymentRequestLine prl ON (prl.FTU_PaymentRequest_ID = pr.FTU_PaymentRequest_ID)"
			+ " INNER JOIN COP_AssemblyRecordLine arl ON (arl.COP_AssemblyRecordLine_ID = prl.COP_AssemblyRecordLine_ID)"
			+ " INNER JOIN COP_AssemblyRecord ar ON (ar.COP_AssemblyRecord_ID = arl.COP_AssemblyRecord_ID)"
			+ " INNER JOIN C_DocType dt ON (dt.C_DocType_ID = pr.C_DocType_ID)"
			+ " INNER JOIN C_Currency cc ON (cc.C_Currency_ID = pr.C_Currency_ID)"
			+ " INNER JOIN AD_Org o ON (arl.AD_Org_ID = o.AD_Org_ID)"
			+ " INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = prl.C_BPartner_ID)"
			+ " INNER JOIN C_BP_BankAccount bpa ON (bpa.C_BP_BankAccount_ID = prl.C_BP_BankAccount_ID)"
			+ " INNER JOIN C_Bank bank ON (bank.C_Bank_ID = bpa.C_Bank_ID)"
			+ " INNER JOIN C_Currency c ON (c.C_Currency_ID = ar.C_Currency_ID)"
			+ " LEFT JOIN ("
				+ "SELECT prl_1.FTU_PaymentRequestLine_ID"
				+ ", SUM(COALESCE(currencyconvert(psl_1.PayAmt, cbacct.C_Currency_ID, pr_1.C_Currency_ID, ps_1.PayDate, ar_1.C_ConversionType_ID, ar_1.AD_Client_ID, ar_1.AD_Org_ID), 0)) AS PayAmt"
				+ " FROM C_PaySelectionLine psl_1"
				+ " INNER JOIN FTU_PaymentRequestLine prl_1 ON (prl_1.FTU_PaymentRequestLine_ID = psl_1.FTU_PaymentRequestLine_ID)"
				+ " INNER JOIN FTU_PaymentRequest pr_1 ON (pr_1.FTU_PaymentRequest_ID = prl_1.FTU_PaymentRequest_ID)"
				+ " INNER JOIN C_PaySelection ps_1 ON (ps_1.C_PaySelection_ID = psl_1.C_PaySelection_ID)"
				+ " INNER JOIN C_BankAccount cbacct ON (cbacct.C_BankAccount_ID = ps_1.C_BankAccount_ID)"
				+ " INNER JOIN COP_AssemblyRecordLine arl_1 ON (arl_1.COP_AssemblyRecordLine_ID = prl_1.COP_AssemblyRecordLine_ID)"
				+ " INNER JOIN COP_AssemblyRecord ar_1 ON (ar_1.COP_AssemblyRecord_ID = arl_1.COP_AssemblyRecord_ID)"
				+ " LEFT JOIN C_PaySelectionCheck psc_1 ON (psc_1.C_PaySelectionCheck_ID = psl_1.C_PaySelectionCheck_ID)"
				+ " WHERE psl_1.IsActive = 'Y' "
				+ " GROUP BY prl_1.FTU_PaymentRequestLine_ID"
			+ ") psl ON (psl.FTU_PaymentRequestLine_ID = prl.FTU_PaymentRequestLine_ID)"
			,
			"(prl.PayAmt - COALESCE(psl.PayAmt, 0)) > 0 "
			+ "AND pr.DocStatus = 'CO' AND ar.DocStatus IN ('CO', 'CL')"
			, true, "pr");
		}
		//End By Argenis Rodríguez
	}   //  dynInit

	/**
	 *  Load Bank Info - Load Info from Bank Account and valid Documents (PaymentRule)
	 */
	public ArrayList<ValueNamePair> getPaymentRuleData(BankInfo bi)
	{
		if (bi == null)
			return null;
		m_bankBalance = bi.Balance;
		
		ArrayList<ValueNamePair> data = new ArrayList<ValueNamePair>();
		
		int AD_Reference_ID = REFERENCE_PAYMENTRULE;  //  MLookupInfo.getAD_Reference_ID("All_Payment Rule");
		Language language = Env.getLanguage(Env.getCtx());
		MLookupInfo info = MLookupFactory.getLookup_List(language, AD_Reference_ID);
		String sql = info.Query.substring(0, info.Query.indexOf(" ORDER BY"))
			+ " AND " + info.KeyColumn
			+ " IN (SELECT PaymentRule FROM C_BankAccountDoc WHERE C_BankAccount_ID=? AND IsActive='Y') "
			+ info.Query.substring(info.Query.indexOf(" ORDER BY"));
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, bi.C_BankAccount_ID);
			rs = pstmt.executeQuery();
			ValueNamePair vp = null;
			while (rs.next())
			{
				vp = new ValueNamePair(rs.getString(2), rs.getString(3));
				data.add(vp);
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
		return data;
	}

	/**
	 *  Query and create TableInfo
	 */
	public void loadTableInfo(BankInfo bi, Timestamp payDate, ValueNamePair paymentRule, boolean onlyDue, 
			boolean onlyPositiveBalance, boolean prePayment, boolean manual, boolean isDividendPayment,MDocType Doctype, int C_BPartner_ID, int FTU_PaymentRequest_ID, KeyNamePair org, IMiniTable miniTable)
	{
		log.config("");
		//  not yet initialized
		if (m_sql == null)
			return;
		// added by Adonis Castellanos to fix error of currency convert function with the date sended with hours,minutes and seconds 
		Calendar cal = Calendar.getInstance(); 
		cal.setTime(payDate);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long time = cal.getTimeInMillis();		
		payDate = new Timestamp(time);
		// End 
		
		String sql = m_sql;
		//  Parameters
		String isSOTrx = "N";
		if (paymentRule != null && X_C_Order.PAYMENTRULE_DirectDebit.equals(paymentRule.getValue()) && !manual)
		{
			isSOTrx = "Y";
			sql += " AND i.PaymentRule='" + X_C_Order.PAYMENTRULE_DirectDebit + "'";
		}
		
		if(Doctype.isSOTrx())
			isSOTrx = "Y";
		//
		if (onlyDue)
			sql += " AND prl.DueDate <= ?";
		//
		//KeyNamePair pp = bpartner;
		//int C_BPartner_ID = pp.getKey();
		if (C_BPartner_ID != 0)
			sql += " AND prl.C_BPartner_ID=?";
		//Document Type
		/*KeyNamePair pr = PaymentRequest;
		int FTU_PaymentRequest_ID  = pr.getKey();*/
		if (FTU_PaymentRequest_ID  != 0)
			sql += " AND prl.FTU_PaymentRequest_ID =?";
		
		//Document Type
		KeyNamePair o = org;
		int ad_org_id  = o.getKey();
		if (ad_org_id != 0)
			sql += " AND pr.ad_org_id =?";
		
		if(manual && Doctype.get_ValueAsString("RequestType").length()>1)
			sql += "AND pr.RequestType = '"+Doctype.get_ValueAsString("RequestType")+"'";

		if (onlyPositiveBalance) {
			int innerindex = sql.indexOf("INNER");
			String subWhereClause = sql.substring(innerindex, sql.length());

			//Replace original aliases with new aliases
			subWhereClause = subWhereClause.replaceAll("\\bpr\\b", "pr1");
			subWhereClause = subWhereClause.replaceAll("\\bprl\\b", "prl1");
			subWhereClause = subWhereClause.replaceAll("\\bprdt\\b", "prdt1");
			subWhereClause = subWhereClause.replaceAll("\\bprc\\b", "prc1");
			subWhereClause = subWhereClause.replaceAll("\\bi\\b", "i1");
			subWhereClause = subWhereClause.replaceAll("\\bo\\b", "o1");
			subWhereClause = subWhereClause.replaceAll("\\bbp\\b", "bp1");
			subWhereClause = subWhereClause.replaceAll("\\bbpba\\b", "bpba1");
			subWhereClause = subWhereClause.replaceAll("\\bb\\b", "b1");
			subWhereClause = subWhereClause.replaceAll("\\bc\\b", "c1");
			subWhereClause = subWhereClause.replaceAll("\\bp\\b", "p1");
			subWhereClause = subWhereClause.replaceAll("\\bpslpay\\b", "pslpay1");
			subWhereClause = subWhereClause.replaceAll("\\bpsl\\b", "psl1");
			subWhereClause = subWhereClause.replaceAll("\\bpsc\\b", "psc1");
			subWhereClause = subWhereClause.replaceAll("\\bpmt\\b", "pmt1");

			sql += " AND prl.c_bpartner_id NOT IN ( SELECT prl1.C_BPartner_ID";
			// PrePayment Order
			/*if(prePayment)
			{
				sql += " FROM FTU_Order_v i1 ";
			}
			// Invoice
			else
			{
				sql += " FROM C_Invoice_v i1 ";
			}*/
			sql += " FROM FTU_PaymentRequest pr1 ";
			sql += subWhereClause
					+ " GROUP BY prl1.C_BPartner_ID";
			/*if(prePayment)
			{
				sql += " HAVING sum(ftuOrderOpen(i1.C_Order_ID, i1.C_OrderPaySchedule_ID)) <= 0) ";
			}
			else
			{
				sql += " HAVING sum(invoiceOpen(i1.C_Invoice_ID, i1.C_InvoicePaySchedule_ID)) <= 0) ";
			}*/
			sql += " HAVING sum(prl1.PayAmt) <= 0) ";
		}

		sql += " ORDER BY 2,3";

		if (log.isLoggable(Level.FINEST)) log.finest(sql + " - C_Currency_ID=" + bi.C_Currency_ID + ", C_BPartner_ID=" + C_BPartner_ID + ", FTU_PaymentRequest_ID=" + FTU_PaymentRequest_ID  + ", AD_Org_ID=" + ad_org_id );
		//  Get Open Invoices
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			int index = 1;
			pstmt = DB.prepareStatement(sql, null);
			/*pstmt.setTimestamp(index++, payDate);		//	DiscountAmt
			pstmt.setInt(index++, bi.C_Currency_ID);
			pstmt.setTimestamp(index++, payDate);
			pstmt.setInt(index++, bi.C_Currency_ID);	//	WriteOffAmt
			pstmt.setTimestamp(index++, payDate);*/
			pstmt.setInt(index++, bi.C_Currency_ID);	//	DueAmt
			pstmt.setTimestamp(index++, payDate);
			//pstmt.setTimestamp(index++, payDate);		//	AmountPay
			pstmt.setInt(index++, bi.C_Currency_ID);//	AmountPay
			pstmt.setTimestamp(index++, payDate);
			if(!manual && !isDividendPayment)
				pstmt.setString(index++, isSOTrx);			//	IsSOTrx
			//pstmt.setInt(index++, m_AD_Client_ID);		//	Client
			if (onlyDue)
				pstmt.setTimestamp(index++, payDate);
			if (C_BPartner_ID != 0)
				pstmt.setInt(index++, C_BPartner_ID);
			if (FTU_PaymentRequest_ID  != 0)                    //Payment Request
				pstmt.setInt(index++, FTU_PaymentRequest_ID );
			if (ad_org_id != 0)                    //Organization
				pstmt.setInt(index++, ad_org_id );
			if (onlyPositiveBalance) {
				if(!manual && !isDividendPayment)
					pstmt.setString(index++, isSOTrx);			//	IsSOTrx
				//pstmt.setInt(index++, m_AD_Client_ID);		//	Client
				if (onlyDue)
					pstmt.setTimestamp(index++, payDate);
				if (C_BPartner_ID != 0)
					pstmt.setInt(index++, C_BPartner_ID);
				if (FTU_PaymentRequest_ID  != 0)                    //Payment Request
					pstmt.setInt(index++, FTU_PaymentRequest_ID );
				if (ad_org_id != 0)                    //Organization
					pstmt.setInt(index++, ad_org_id);				
			}
			//
			rs = pstmt.executeQuery();
			miniTable.loadTable(rs);
		}
		catch (SQLException e)
		{
			throw new DBException(e);
		}
		catch (Exception e)
		{
			throw new AdempiereException(e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
	}   //  loadTableInfo

	/**
	 *  Calculate selected rows.
	 *  - add up selected rows
	 */
	public String calculateSelection(IMiniTable miniTable, boolean isDividendPayment)
	{
		m_noSelected = 0;
		BigDecimal invoiceAmt = Env.ZERO;

		int rows = miniTable.getRowCount();
		for (int i = 0; i < rows; i++)
		{
			IDColumn id = (IDColumn)miniTable.getValueAt(i, 0);
			if (id.isSelected())
			{
				BigDecimal amt = (BigDecimal)miniTable.getValueAt(i, isDividendPayment ? 11 : 12);
				if (amt != null)
					invoiceAmt = invoiceAmt.add(amt);
				m_noSelected++;
			}
		}

		//  Information
		BigDecimal remaining = m_bankBalance.subtract(invoiceAmt);
		StringBuilder info = new StringBuilder();
		info.append(m_noSelected).append(" ").append(Msg.getMsg(Env.getCtx(), "Selected")).append(" - ");
		info.append(m_format.format(invoiceAmt)).append(", ");
		info.append(Msg.getMsg(Env.getCtx(), "Remaining")).append(" ").append(m_format.format(remaining));
		return info.toString();
	}   //  calculateSelection

	public Trx trx = null;
	
	/**
	 *  Generate PaySelection
	 */
	public String generatePaySelect(IMiniTable miniTable, ValueNamePair paymentRule, Timestamp payDate, BankInfo bi, boolean isPrepayment
			, boolean isManual, boolean isDividendPayment, int C_DocType_ID)
	{
		log.info("");

		String trxName = null;
		Trx trx = null;
		try {
			trxName = Trx.createTrxName("PaySelect");
			trx = Trx.get(trxName, true);
			trx.setDisplayName(getClass().getName()+"_generatePaySelect");
			
			String PaymentRule = paymentRule.getValue();
			//  Create Header
			m_ps = new MPaySelection(Env.getCtx(), 0, trxName);
			//	Set DocumentType and DocumentNo
			MDocType dt = new MDocType(Env.getCtx(), C_DocType_ID, trxName);
			MDocType dtTo = new MDocType(Env.getCtx(), dt.get_ValueAsInt("C_DocTypePaySelection_ID"), trxName);
			m_ps.set_ValueOfColumn("C_DocType_ID", dtTo.getC_DocType_ID());
			if(dtTo.getDocNoSequence_ID()>0)
				m_ps.set_ValueOfColumn("DocumentNo", MSequence.getDocumentNo(dtTo.getC_DocType_ID(), trxName, false, null));
			//	Set Organization
			MBankAccount ba = new MBankAccount(Env.getCtx(), bi.C_BankAccount_ID, null);
			m_ps.setAD_Org_ID(ba.getAD_Org_ID());
			m_ps.setName ((m_ps.get_ValueAsString("DocumentNo")!="" ? m_ps.get_ValueAsString("DocumentNo") : "Orden de Pago")
					+ " - " + paymentRule.getName()
					+ " - " + payDate);
			m_ps.setPayDate (payDate);
			m_ps.setC_BankAccount_ID(bi.C_BankAccount_ID);
			m_ps.setIsApproved(true);
			m_ps.setIsOnePaymentPerInvoice(m_isOnePaymentPerInvoice);
			m_ps.saveEx();
			if (log.isLoggable(Level.CONFIG)) log.config(m_ps.toString());

			//  Create Lines
			int rows = miniTable.getRowCount();
			int line = 0;
			for (int i = 0; i < rows; i++)
			{
				IDColumn id = (IDColumn)miniTable.getValueAt(i, 0);
				if (id.isSelected())
				{
					line += 10;
					MPaySelectionLine psl = new MPaySelectionLine (m_ps, line, PaymentRule);
					int C_Invoice_ID = id.getRecord_ID().intValue();
					BigDecimal OpenAmt = (BigDecimal)miniTable.getValueAt(i, isDividendPayment ? 10 : 11);
					BigDecimal DiscountAmt = BigDecimal.ZERO;//(BigDecimal)miniTable.getValueAt(i, 8);
					BigDecimal WriteOffAmt = BigDecimal.ZERO;//(BigDecimal)miniTable.getValueAt(i, 9);
					BigDecimal PayAmt = (BigDecimal)miniTable.getValueAt(i, isDividendPayment ? 11 : 12);
					
					//	Get KeyNamePair Objects
					KeyNamePair pp = (KeyNamePair)miniTable.getValueAt(i, 3);   //  3-PaymentRequestLine
					int prl_ID = pp.getKey();
					KeyNamePair ppbp = (KeyNamePair)miniTable.getValueAt(i, 5);   //  5-BPartner
					int bp_ID = ppbp.getKey();
					
					boolean isSOTrx = false;
					if (paymentRule != null && X_C_Order.PAYMENTRULE_DirectDebit.equals(paymentRule.getValue()) && !isManual)
						isSOTrx = true;
					//
					psl.set_ValueOfColumn("C_BPartner_ID", bp_ID);
					psl.set_ValueOfColumn("FTU_PaymentRequestLine_ID", prl_ID);
					if(!isPrepayment && !isManual && !isDividendPayment)
					{
						psl.setInvoice(C_Invoice_ID, isSOTrx,
								OpenAmt, PayAmt, DiscountAmt, WriteOffAmt);	
					}
					else if(isPrepayment && !isManual)
					{
						//	Set Value to Order
						psl.set_ValueOfColumn("C_Order_ID", C_Invoice_ID);
						psl.setIsSOTrx(isSOTrx);
						psl.setOpenAmt(OpenAmt);
						psl.setPayAmt (PayAmt);
						psl.setDiscountAmt(DiscountAmt);
						psl.setWriteOffAmt(WriteOffAmt);
						psl.setDifferenceAmt(OpenAmt.subtract(PayAmt).subtract(DiscountAmt).subtract(WriteOffAmt));
					}
					else
					{
						psl.setIsSOTrx(false);
						psl.setOpenAmt(OpenAmt);
						psl.setPayAmt (PayAmt);
						psl.setDiscountAmt(DiscountAmt);
						psl.setWriteOffAmt(WriteOffAmt);
						psl.setDifferenceAmt(OpenAmt.subtract(PayAmt).subtract(DiscountAmt).subtract(WriteOffAmt));
					}
					psl.saveEx(trxName);
					//	Check Prepared Payment Request Line 
					MFTUPaymentRequestLine prl = new MFTUPaymentRequestLine(Env.getCtx(), prl_ID, trxName);
					prl.setIsPrepared(true);
					prl.saveEx(trxName);
					
					if (log.isLoggable(Level.FINE) && !isPrepayment) log.fine("C_Invoice_ID=" + C_Invoice_ID + ", PayAmt=" + PayAmt);
					if (log.isLoggable(Level.FINE) && isPrepayment) log.fine("C_Order_ID=" + C_Invoice_ID + ", PayAmt=" + PayAmt);
				}
			}   //  for all rows in table
		} catch (Exception e) {
			if (trx != null) {
				trx.rollback();
				trx.close();
				trx = null;
			}
			m_ps = null;
			throw new AdempiereException(e);
		} finally {
			if (trx != null) {
				trx.commit();
				trx.close();
			}
		}
		
		return null;
	}   //  generatePaySelect

	/**************************************************************************
	 *  Bank Account Info
	 */
	public static class BankInfo
	{
		/**
		 * 	BankInfo
		 *	@param newC_BankAccount_ID
		 *	@param newC_Currency_ID
		 *	@param newName
		 *	@param newCurrency
		 *	@param newBalance
		 *	@param newTransfers
		 */
		public BankInfo (int newC_BankAccount_ID, int newC_Currency_ID,
			String newName, String newCurrency, BigDecimal newBalance, boolean newTransfers)
		{
			C_BankAccount_ID = newC_BankAccount_ID;
			C_Currency_ID = newC_Currency_ID;
			Name = newName;
			Currency = newCurrency;
			Balance = newBalance;
		}
		int C_BankAccount_ID;
		int C_Currency_ID;
		String Name;
		public String Currency;
		public BigDecimal Balance;
		boolean Transfers;

		/**
		 * 	to String
		 *	@return info
		 */
		public String toString()
		{
			return Name;
		}
	}   //  BankInfo

}
