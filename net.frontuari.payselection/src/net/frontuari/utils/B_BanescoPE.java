/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2013 E.R.P. Consultores y Asociados, C.A.               *
 * All Rights Reserved.                                                       *
 * Contributor(s): Yamel Senih www.erpconsultoresyasociados.com               *
 *****************************************************************************/
//Add import payment class
package net.frontuari.utils;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MClient;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.PaymentExport;

/**
 * @author <a href="mailto:jlct.master@gmail.com">Jorge Colmenarez</a>
 * Export class for Banesco Bank
 */
public class B_BanescoPE implements PaymentExport {

	/** Logger								*/
	static private CLogger	s_log = CLogger.getCLogger (B_BanescoPE.class);

	/** BPartner Info Index for Account    		*/
	private static final int     BPA_A_ACCOUNT 		= 0;
	/** BPartner Info Index for Value       	*/
	private static final int     BPA_A_IDENT_SSN 	= 1;
	/** BPartner Info Index for Name        	*/
	private static final int     BPA_A_NAME 		= 2;
	/** BPartner Info Index for Swift Code	    */
	private static final int     BPA_SWIFTCODE 		= 3;
	/** BPartner Info Index for e-mail    		*/
	private static final int     BPA_A_EMAIL 		= 4;
	/**	Type Register */
	private String p_Type_Register					= "";
	
	/**************************************************************************
	 *  Export to File
	 *  @param checks array of checks
	 *  @param file file to export checks
	 *  @return number of lines
	 */
	public int exportToFile (MPaySelectionCheck[] checks, File file, StringBuffer err)
	{
		if (checks == null || checks.length == 0)
			return 0;
		//  delete if exists
		try
		{
			if (file.exists())
				file.delete();
		}
		catch (Exception e)
		{
			s_log.log(Level.WARNING, "Could not delete - " + file.getAbsolutePath(), e);
		}
		
		//	Set Objects
		MPaySelection m_PaySelection = (MPaySelection) checks[0].getC_PaySelection();
		MBankAccount m_BankAccount = (MBankAccount) m_PaySelection.getC_BankAccount();
		MOrgInfo m_OrgInfo = MOrgInfo.get(m_PaySelection.getCtx(), m_PaySelection.getAD_Org_ID(), m_PaySelection.get_TrxName());
		MClient m_Client = MClient.get(m_OrgInfo.getCtx(), m_OrgInfo.getAD_Client_ID());
		MBank mBank = MBank.get(m_BankAccount.getCtx(), m_BankAccount.getC_Bank_ID());
				
		//	Format Date Header
		String formatH = "yyyyMMddHHmmss";
		SimpleDateFormat sdfH = new SimpleDateFormat(formatH);
		//	Format Date
		String format = "yyyyMMdd";
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		// Bank Identification
		String bankIdentification = mBank.getSwiftCode();
		// Raul Muñoz 2016-02-02 10:00am
		//	Valid Null Value
		if(bankIdentification == null)
			bankIdentification = "";
		
		bankIdentification.trim();
		bankIdentification = String.format("%1$-" + 12 + "s", bankIdentification);

		//	Fields of Control Register (fixed data)
		String p_Commercial_Allocated = String.format("%1$-" + 15 + "s", "BANESCO");
		String p_Standard_EDIFACT = "E";
		String p_Version_Standard_EDIFACT ="D  95B";
		String p_Document_Type = "PAYMUL";
		String p_Production = "P";
		//	End Fields 
		
		//	Fields of Header Register 
		String p_Type_Transaction = "SCV";
		String p_Description_Code = String.format("%1$-" + 32 + "s", "");
		String p_PaymentRequest_Condition = String.format("%1$-" + 3 + "s","9");
		String p_PaymentRequestNo = String.format("%1$-" + 35 + "s", m_PaySelection.get_ValueAsString("DocumentNo"));
		String p_PaymentRequestDate = sdfH.format(m_PaySelection.getPayDate());
		//	End Fields
		
		//	Fields of Debit Register
		String p_DebitReferenceNo = String.format("%0"+ 8 +"d",Integer.parseInt(checks[0].getDocumentNo()));
		//	Process Organization Tax ID
		String p_orgTaxID = m_OrgInfo.getTaxID().replace("-", "").trim();
		p_orgTaxID = String.format("%1$-" + 17 + "s", p_orgTaxID);
		String p_clientName = String.format("%1$-"+ 35 +"s", m_Client.getName());
		//	Payment Amount
		String p_totalAmt = String.format("%.2f", m_PaySelection.getTotalAmt().abs()).replace(".", "").replace(",", "");
		//	Modified by Jorge Colmenarez 2015-08-09
		//	Add Support for Big Integer and Solved NumberFormatException when value is major 2147483648 
		BigInteger bigInt = new BigInteger(p_totalAmt, 10);
		p_totalAmt = String.format("%0" + 15 + "d", bigInt);
		//	End Jorge Colmenarez
		String p_ISO_Code = "VES";
		String p_Free_Field = String.format("%1$-"+ 1 +"s","");
		//	Account No
		String p_bankAccountNo = m_BankAccount.getAccountNo().trim();
		p_bankAccountNo = p_bankAccountNo.substring(0, (p_bankAccountNo.length() >= 20 ? 20: p_bankAccountNo.length()));
		p_bankAccountNo = p_bankAccountNo.replace(" ", "");
		p_bankAccountNo = String.format("%1$-" + 34 + "s", p_bankAccountNo);
		String p_BankCodeOrder = String.format("%1$-"+ 11 +"s","BANESCO");
		String p_PayDate = sdf.format(m_PaySelection.getPayDate());
		
		BigDecimal totalAmt = BigDecimal.ZERO;
		//	End Fields 
		
		
		int noLines = 0;
		StringBuffer line = null;
		try
		{
			FileWriter fw = new FileWriter(file);
			
			// 	Write Control Register
			line = new StringBuffer();
			//	Set Value Type Register for Control Register
			p_Type_Register = "HDR";
			// 	Control Register
			line.append(p_Type_Register)												//	Type Register
				.append(p_Commercial_Allocated)											//	Commercial Allocated
				.append(p_Standard_EDIFACT)												//	Standard EDIFACT
				.append(p_Version_Standard_EDIFACT)										//	Version Standard EDIFACT
				.append(p_Document_Type)												//	Document Type
				.append(p_Production);													//	Production
			
			fw.write(line.toString());
			noLines++;
			
			//  Write Header
			line = new StringBuffer();
			//	Set Value Type Register for Header Register
			p_Type_Register = "01";
			//	Header
			line.append(Env.NL)															//	New Line
				.append(p_Type_Register)												//  Type Register
				.append(p_Type_Transaction)												//	Type Transaction
				.append(p_Description_Code)												//  Description Code
				.append(p_PaymentRequest_Condition)										//  Payment Request Condition
				.append(p_PaymentRequestNo)												//  Payment Request Number
				.append(p_PaymentRequestDate);											//  Payment Request Date
				
			fw.write(line.toString());
			noLines++;
			
			//  Write Debit Note
			/*line = new StringBuffer();
			//	Set Value Type Register for Debit Note Register
			p_Type_Register = "02";
			
			//	Debit Note
			line.append(Env.NL)															//	New Line
				.append(p_Type_Register)												//  Type Register
				.append(String.format("%1$-"+ 30 +"s",p_DebitReferenceNo))				//	Reference Number
				.append(p_orgTaxID)														//  Organization Tax ID
				.append(p_clientName)													//  Client Name
				.append(p_totalAmt)														//  Total Amt
				.append(p_ISO_Code)														//  ISO Code Currency
				.append(p_Free_Field)													//  Free Field
				.append(p_bankAccountNo)												//  Bank Account Number
				.append(p_BankCodeOrder)												//  Bank Order Code
				.append(p_PayDate);														//  Payment Date 
				
			fw.write(line.toString());
			noLines++;*/
			int con=0;
			//  Write Credit Note
			for (int i = 0; i < checks.length; i++)
			{
				con++;
				//	Set Objects 
				MPaySelectionCheck mpp = checks[i];
				if (mpp == null)
					continue;
				//  BPartner Info
				String bp[] = getBPartnerInfo(mpp.getC_BPartner_ID(), mpp);
				
				//	Payment Detail
				//	Credit Register
				//	Process Document No
				String p_docNo = mpp.getDocumentNo();
				p_docNo = p_docNo.substring(0, (p_docNo.length() >= 8? 8: p_docNo.length()));
				p_docNo = String.format("%0"+ 8 +"d",Integer.parseInt(mpp.getDocumentNo()));
				BigDecimal paymentAmt = mpp.getPayAmt().abs();
				
				totalAmt = totalAmt.add(paymentAmt.setScale(2, RoundingMode.HALF_UP));
				//	Payment Amount
				String p_Amt = String.format("%.2f", paymentAmt).replace(".", "").replace(",", "");
				//	Modified By Jorge Colmenarez 2015-02-19 
				//	Changed Method parseInt because the string value is major that 2147483647
				//	referenced Integer.class Line 397      * parseInt("2147483647", 10) returns 2147483647
				if(p_Amt.length() <= 9) 
					p_Amt = String.format("%0" + 15 + "d", Integer.parseInt(p_Amt));
				else{
					String zero="";
					//	the value 15 are the characters should have the field
					int rest = 15-p_Amt.length();
					for(int j=0;j<rest;j++){
						zero = zero+"0";
					}
					p_Amt = zero+p_Amt;
				}
				//	End Jorge Colmenarez
				//	Used the variable p_ISO_Code
				String p_BP_BankAccount = String.format("%1$-"+ 30 +"s",bp[BPA_A_ACCOUNT]); 
				String p_BP_SwiftCode = String.format("%1$-"+ 11 +"s", bp[BPA_SWIFTCODE]);
				String p_AgencyCode = String.format("%1$-"+ 3 +"s","");
				String p_BP_TaxID = String.format("%1$-"+ 17 +"s",bp[BPA_A_IDENT_SSN]);
				String p_BP_Name = String.format("%1$-"+ 70 +"s",bp[BPA_A_NAME]);
				String p_BP_Email = String.format("%1$-"+ 70 +"s",bp[BPA_A_EMAIL]);
				String p_BP_Phone = String.format("%1$-"+ 25 +"s","");
				String p_BP_TaxID_Contact = String.format("%1$-"+ 17 +"s","");
				String p_BP_Name_Contact = String.format("%1$-"+ 35 +"s","");
				String p_Settlor_Qualifier = String.format("%1$-"+ 1 +"s","");
				String p_Card_Employee = String.format("%1$-"+ 30 +"s","");
				String p_Type_Payroll = String.format("%1$-"+ 2 +"s","");
				String p_Location = String.format("%1$-"+ 21 +"s","");
				String p_PaymentTerm = "";
				//2015-02-20 Carlos Parada Compare Bank of Payment
				if (mpp.getC_BP_BankAccount_ID() != 0){
					//MBPBankAccount ba = new MBPBankAccount (mpp.getCtx(), mpp.getC_BP_BankAccount_ID(), mpp.get_TrxName());
					//if (ba.getC_Bank_ID() != 0 ){
						//MBank bank = new MBank(Env.getCtx(), ba.getC_Bank_ID(), ba.get_TrxName());
						if (mBank.getRoutingNo().equals(bp[BPA_SWIFTCODE]))
							p_PaymentTerm = String.format("%1$-"+ 3 +"s","42");
					//}
				}
				if (p_PaymentTerm.equals(""))
					p_PaymentTerm = String.format("%1$-"+ 3 +"s","425");
				//End Carlos Parada
				
				line = new StringBuffer();
				//	Set Value Type Register for Debit Note Register
				p_Type_Register = "02";
				
				//	Debit Note
				line.append(Env.NL)															//	New Line
					.append(p_Type_Register)												//  Type Register
					.append(String.format("%1$-"+ 30 +"s",p_docNo))							//	Reference Number
					.append(p_orgTaxID)														//  Organization Tax ID
					.append(p_clientName)													//  Client Name
					.append(p_Amt)															//  Total Amt
					.append(p_ISO_Code)														//  ISO Code Currency
					.append(p_Free_Field)													//  Free Field
					.append(p_bankAccountNo)												//  Bank Account Number
					.append(p_BankCodeOrder)												//  Bank Order Code
					.append(p_PayDate);														//  Payment Date 
					
				fw.write(line.toString());
				noLines++;
				
				//	Write Credit Register
				p_Type_Register = "03";
				
				line = new StringBuffer();
				
				line.append(Env.NL)															//	New Line
					.append(p_Type_Register)												//	Type Register	
					.append(String.format("%1$-"+ 30 +"s",p_docNo))							//	Document Number
					.append(p_Amt)															// 	Payment Amount
					.append(p_ISO_Code)														//	ISO Code Currency
					.append(p_BP_BankAccount)												//  BP Bank Account
					.append(p_BP_SwiftCode)													// 	BP Bank Swift Code
					.append(p_AgencyCode)													// 	Agency Code
					.append(p_BP_TaxID)														// 	BP TaxID
					.append(p_BP_Name)														//	BP Name
					.append(p_BP_Email)														//	BP Email
					.append(p_BP_Phone)														//  BP Phone
					.append(p_BP_TaxID_Contact)												//  BP TaxID Contact
					.append(p_BP_Name_Contact)												// 	BP Name Contact
					.append(p_Settlor_Qualifier)											//	Settlor Qualifier
					.append(p_Card_Employee)												// 	Card Employee
					.append(p_Type_Payroll)													//	Type Payroll
					.append(p_Location)														//	Location						//	Payment Concept
					.append(p_PaymentTerm); 												//	Payment Term
				
				fw.write(line.toString());
				noLines++;
			}   // End Write Credit Note
			
			//	Totals Register
			//	Set Value Type Register for Totals Register
			p_Type_Register = "06";
			String p_CountDebit = String.format("%0"+ 15 +"d",con);
			String p_CountCredit = String.format("%0"+ 15 +"d",con);
			p_totalAmt = String.format("%.2f", totalAmt).replace(".", "").replace(",", "");
			bigInt = new BigInteger(p_totalAmt, 10);
			p_totalAmt = String.format("%0" + 15 + "d", bigInt);
			//	Set p_TotalAmt
			
			//	Write Totals
			line = new StringBuffer();
			
			line.append(Env.NL)															//	New Line
				.append(p_Type_Register)												//  Type Register
				.append(p_CountDebit)													//	Count Debit
				.append(p_CountCredit)													//  Count Credit
				.append(p_totalAmt);													//  Total Amount
				
			fw.write(line.toString());
			noLines++;
			//	Close
			 
			fw.flush();
			fw.close();
		}
		catch (Exception e)
		{
			err.append(e.toString());
			s_log.log(Level.SEVERE, "", e);
			return -1;
		}

		return noLines;
	}   //  exportToFile

	/**
	 * Get Business Partner Information
	 * @author <a href="mailto:yamelsenih@gmail.com">Yamel Senih</a> 16/04/2013, 20:14:38
	 * @param C_BPartner_ID
	 * @return
	 * @return String[]
	 */
	private String[] getBPartnerInfo (int C_BPartner_ID ,MPaySelectionCheck mpp)
	{
		String sql = null ;
		String[] bp = new String[5];
		//	Sql
		if (mpp.getC_BP_BankAccount_ID()==0)
			sql = "SELECT MAX(bpa.AccountNo) AccountNo, bpa.A_Ident_SSN, bpa.A_Name, bpb.RoutingNo AS SwiftCode, bpa.A_Email " +
					"FROM C_BP_BankAccount bpa " +
					"INNER JOIN C_Bank bpb ON(bpb.C_Bank_ID = bpa.C_Bank_ID) " +
					"WHERE bpa.C_BPartner_ID = ? " +
					"AND bpa.IsActive = 'Y' " +
					"AND bpa.IsACH = 'Y' " +
					"GROUP BY bpa.C_BPartner_ID, bpa.A_Ident_SSN, bpa.A_Name, bpb.RoutingNo, bpa.A_Email";
		else
			sql = "SELECT AccountNo, A_Ident_SSN, A_Name, bpb.RoutingNo AS SwiftCode , bpa.A_Email " +
					"FROM C_BP_BankAccount bpa " +
					"LEFT JOIN C_Bank bpb ON(bpb.C_Bank_ID = bpa.C_Bank_ID) " +
					"WHERE bpa.C_BP_BankAccount_ID = ? " ;
		
		s_log.fine("SQL=" + sql);
		
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			if (mpp.getC_BP_BankAccount_ID()==0)
				pstmt.setInt(1, C_BPartner_ID);
			else
				pstmt.setInt(1, mpp.getC_BP_BankAccount_ID());
			
			ResultSet rs = pstmt.executeQuery();
			//
			if (rs.next())
			{
				bp[BPA_A_ACCOUNT] = rs.getString(1);
				if (bp[BPA_A_ACCOUNT] == null)
					bp[BPA_A_ACCOUNT] = "NO CUENTA";
				bp[BPA_A_IDENT_SSN] = rs.getString(2);
				if (bp[BPA_A_IDENT_SSN] == null)
					bp[BPA_A_IDENT_SSN] = "NO RIF/CI";
				bp[BPA_A_NAME] = rs.getString(3);
				if (bp[BPA_A_NAME] == null)
					bp[BPA_A_NAME] = "NO NOMBRE";
				bp[BPA_SWIFTCODE] = rs.getString(4);
				if (bp[BPA_SWIFTCODE] == null)
					bp[BPA_SWIFTCODE] = "NO SWIFT";
				bp[BPA_A_EMAIL] = rs.getString(5);
				if (bp[BPA_A_EMAIL] == null)
					bp[BPA_A_EMAIL] = "";
			} else {
				bp[BPA_A_ACCOUNT] 	= "NO CUENTA";
				bp[BPA_A_IDENT_SSN] = "NO RIF/CI";
				bp[BPA_A_NAME] 		= "NO NOMBRE";
				bp[BPA_SWIFTCODE] 	= "NO SWIFT";
				bp[BPA_A_EMAIL] 	= "";
			}
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			s_log.log(Level.SEVERE, sql, e);
		}
		return processBPartnerInfo(bp);
	}   //  getBPartnerInfo
	
	
	/**
	 * Process Business Partner Information
	 * @author <a href="mailto:yamelsenih@gmail.com">Yamel Senih</a> 16/04/2013, 20:14:49
	 * @param bp
	 * @return
	 * @return String[]
	 */
	private String [] processBPartnerInfo(String [] bp){
		//	Process Business Partner Account No
		String bpaAccount = bp[BPA_A_ACCOUNT];
		bpaAccount = bpaAccount.substring(0, bpaAccount.length() >= 20? 20: bpaAccount.length());
		bpaAccount = String.format("%1$-" + 20 + "s", bpaAccount).replace(" ","0");
		bp[BPA_A_ACCOUNT] = bpaAccount;
		//	Process Tax ID
		//	Modified by Jorge Colmenarez 2015-04-30
		//	Add Support for Complete TaxID with 0 if length is minor that 9 digites
		String letterTaxID = "";
		String bpaTaxID = bp[BPA_A_IDENT_SSN];
		if(!bpaTaxID.equals("NO RIF/CI")){
			bpaTaxID = bpaTaxID.replace("-", "").trim();
			//	Extract Letter of TaxID
			letterTaxID = bpaTaxID.substring(0,1);
			//	Extract Number of TaxID
			bpaTaxID = bpaTaxID.substring(1,bpaTaxID.length());
			if(bpaTaxID.length()<9){
				bpaTaxID = String.format("%0"+ 9 +"d",Integer.parseInt(bpaTaxID));
			}
			//	Join Letter with Number
			bpaTaxID = letterTaxID+bpaTaxID;
			bpaTaxID = bpaTaxID.substring(0, bpaTaxID.length() >= 15? 15: bpaTaxID.length());
		}
		bp[BPA_A_IDENT_SSN] = bpaTaxID;
		//	Process Account Name
		//	Using Method replaceSpecialCharacters with value of bp[BPA_A_NAME]
		String bpaName = replaceSpecialCharacters(bp[BPA_A_NAME]);
		//	End Jorge Colmenarez
		bpaName = bpaName.substring(0, bpaName.length() >= 60? 60: bpaName.length());
		bpaName = String.format("%1$-" + 60 + "s", bpaName);
		bp[BPA_A_NAME] = bpaName;
		//	Process Swift Code
		String bpaSwiftCode = bp[BPA_SWIFTCODE];
		bpaSwiftCode = bpaSwiftCode.substring(0, bpaSwiftCode.length() >= 12? 12: bpaSwiftCode.length());
		
		bp[BPA_SWIFTCODE] = bpaSwiftCode;
		//	Process e-mail
		String bpaEmail = bp[BPA_A_EMAIL];
		bpaName = bpaEmail.substring(0, bpaEmail.length() >= 60? 60: bpaEmail.length());
		bpaEmail = String.format("%1$-" + 50 + "s", bpaEmail);
		bp[BPA_A_EMAIL] = bpaEmail;
		return bp;
	}	//	processBPartnerInfo
	
	/**
	 * Function that removes accents and special characters in a string.
	 * @author <a href="mailto:jlct.master@gmail.com">Jorge Colmenarez</a> 30/04/2015, 14:42:24
	 * @param input
	 * @return clean text string accents and special characters.
	 */
	public static String replaceSpecialCharacters(String input) {
	    // Original string to be replaced.
	    String original = "áàäéèëíìïóòöúùuñÁÀÄÉÈËÍÌÏÓÒÖÚÙÜÑçÇ";
	    // ASCII character string that will replace the original.
	    String ascii = "aaaeeeiiiooouuunAAAEEEIIIOOOUUUNcC";
	    String output = input;
	    for (int i=0; i<original.length(); i++) {
	        // Reemplazamos los caracteres especiales.
	        output = output.replace(original.charAt(i), ascii.charAt(i));
	    }//for i
	    return output;
	}//replaceSpecialCharacters

	@Override
	public String getFilenamePrefix() {
		String creationDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(System.currentTimeMillis()) ;
		return "Banesco-" + creationDate ;
	}

	@Override
	public String getFilenameSuffix() {
		return ".txt";
	}

	@Override
	public String getContentType() {
		return "text/plain";
	}

	@Override
	public boolean supportsDepositBatch() {
		return true;
	}

	@Override
	public boolean supportsSeparateBooking() {
		return true;
	}

	@Override
	public boolean getDefaultDepositBatch() {
		return false;
	}
}
