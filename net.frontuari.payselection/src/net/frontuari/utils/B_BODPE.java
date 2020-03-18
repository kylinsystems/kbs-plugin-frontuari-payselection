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
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.logging.Level;

import org.compiere.model.MBPBankAccount;
import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.PaymentExport;

/**
 * @author <a href="mailto:jlct.master@gmail.com">Jorge Colmenarez</a>
 * Export class for BOD Bank
 */
public class B_BODPE implements PaymentExport {

	/** Logger								*/
	static private CLogger	s_log = CLogger.getCLogger (B_BODPE.class);

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
	/** BPartner Info Index for e-mail    		*/
	private static final int     BPA_BANKROUTE 		= 5;
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
		MBank mBank = MBank.get(m_BankAccount.getCtx(), m_BankAccount.getC_Bank_ID());
		
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
		
		//	Fields of Header Register 
		String p_Description = String.format("%1$-" + 20 + "s", "PROVEEDORES");
		String p_BankContract = String.format("%0" + 17 + "d",Integer.parseInt("0")); // mBank.getContract
		String p_PaymentRequestDate = sdf.format(m_PaySelection.getPayDate());
		String p_PaymentsQty = String.format("%0" + 6 + "d", checks.length);
		//	Fields of Debit Register
		String p_DebitReferenceNo = String.format("%0"+ 8 +"d",Integer.parseInt(checks[0].getDocumentNo()));
		//	Process Organization Tax ID
		String p_orgTaxID = m_OrgInfo.getTaxID().replace("-", "").trim();
		p_orgTaxID = String.format("%1$-" + 10 + "s", p_orgTaxID);
		//	Payment Amount
		String p_totalAmt = String.format("%.2f", m_PaySelection.getTotalAmt().abs()).replace(".", "").replace(",", "");
		//	Modified by Jorge Colmenarez 2015-08-09
		//	Add Support for Big Integer and Solved NumberFormatException when value is major 2147483648 
		BigInteger bigInt = new BigInteger(p_totalAmt, 10);
		p_totalAmt = String.format("%0" + 15 + "d", bigInt);
		//	End Jorge Colmenarez
		String p_ISO_Code = "VES";
		//	End Header Fields
		String p_Free_Field = String.format("%1$-"+ 158 +"s","");
		//	Account No
		String p_bankAccountNo = m_BankAccount.getAccountNo().trim();
		p_bankAccountNo = p_bankAccountNo.substring(0, (p_bankAccountNo.length() >= 20 ? 20: p_bankAccountNo.length()));
		p_bankAccountNo = p_bankAccountNo.replace(" ", "");
		p_bankAccountNo = String.format("%1$-" + 34 + "s", p_bankAccountNo);
		//	End Fields 
		
		
		int noLines = 0;
		StringBuffer line = null;
		try
		{
			FileWriter fw = new FileWriter(file);			
			//  Write Header
			line = new StringBuffer();
			//	Set Value Type Register for Header Register
			p_Type_Register = "01";
			//	Header
			line.append(p_Type_Register)												//  Type Register
				.append(p_Description)													//	Description Lote
				.append(p_orgTaxID)														//  TaxID
				.append(p_BankContract)													//  Bank Contract
				.append(p_DebitReferenceNo)												//  Debit Reference No
				.append(p_PaymentRequestDate)											//  Payment Date
				.append(p_PaymentsQty)													//  Payment Qty
				.append(p_totalAmt)														//  Total Amt
				.append(p_ISO_Code)														//  Currency
				.append(p_Free_Field);													//  FILLER
				
			fw.write(line.toString());
			noLines++;
			
			int con=0;
			//  Write Details
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
				p_Type_Register = "02";
				//	Process Document No
				String p_docNo = mpp.getDocumentNo();
				p_docNo = p_docNo.substring(0, (p_docNo.length() >= 8? 8: p_docNo.length()));
				p_docNo = String.format("%0"+ 8 +"d",Integer.parseInt(mpp.getDocumentNo()));
				//	Payment Amount
				String p_Amt = String.format("%.2f", mpp.getPayAmt().abs()).replace(".", "").replace(",", "");
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
				String p_BP_BankAccount = String.format("%1$-"+ 20 +"s",bp[BPA_A_ACCOUNT]); 
				String p_BP_RoutingNo = String.format("%0"+ 4 +"d", Integer.parseInt(bp[BPA_BANKROUTE]));
				String p_BP_TaxID = String.format("%1$-"+ 10 +"s",bp[BPA_A_IDENT_SSN]);
				String p_BP_Name = String.format("%1$-"+ 60 +"s",bp[BPA_A_NAME]);
				String p_BP_Email = String.format("%1$-"+ 40 +"s",bp[BPA_A_EMAIL]);
				String p_BP_Phone = String.format("%0"+ 11 +"d",Integer.parseInt("0"));
				String p_PaymentTerm = "";
				//2015-02-20 Carlos Parada Compare Bank of Payment
				if (mpp.getC_BP_BankAccount_ID() != 0){
					MBPBankAccount ba = new MBPBankAccount (mpp.getCtx(), mpp.getC_BP_BankAccount_ID(), mpp.get_TrxName());
					if (ba.getC_Bank_ID() != 0 ){
						MBank bank = new MBank(Env.getCtx(), ba.getC_Bank_ID(), ba.get_TrxName());
						if (bank.getSwiftCode().equals(bp[BPA_SWIFTCODE]))
							p_PaymentTerm = String.format("%1$-"+ 3 +"s","CTA");
					}
				}
				if (p_PaymentTerm.equals(""))
					p_PaymentTerm = String.format("%1$-"+ 3 +"s","BAN");
				//End Carlos Parada
				//	Write Credit Register
				line = new StringBuffer();
				
				line.append(Env.NL)															//	New Line
					.append(p_Type_Register)												//	Type Register
					.append(p_BP_TaxID)														// 	BP TaxID	
					.append(p_BP_Name)														//	BP Name
					.append(String.format("%0"+ 9 +"d",Integer.parseInt(p_docNo)))			//	Document Number
					.append(String.format("%1$-"+ 30 +"s",""))								//	Description
					.append(p_PaymentTerm)	 												//	Payment Term
					.append(p_BP_BankAccount)												//  BP Bank Account
					.append(p_BP_RoutingNo)													//  BP Bank Routing
					.append(p_PaymentRequestDate)											// 	Payment Date
					.append(p_Amt)															// 	Payment Amount
					.append(p_ISO_Code)														//	ISO Code Currency
					.append(String.format("%0"+ 15 +"d",Integer.parseInt("0")))				// 	Withholding Amt
					.append(p_BP_Email)														//	BP Email
					.append(p_BP_Phone)														//  BP Phone
					.append(String.format("%1$-"+ 20 +"s",""));								//  FILLER
				
				fw.write(line.toString());
				noLines++;
			}   // End Write Detail
			 
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
		String[] bp = new String[6];
		//	Sql
		if (mpp.getC_BP_BankAccount_ID()==0)
			sql = "SELECT MAX(bpa.AccountNo) AccountNo, bpa.A_Ident_SSN, bpa.A_Name, bpb.SwiftCode, bpa.A_Email, bpb.RoutingNo " +
					"FROM C_BP_BankAccount bpa " +
					"INNER JOIN C_Bank bpb ON(bpb.C_Bank_ID = bpa.C_Bank_ID) " +
					"WHERE bpa.C_BPartner_ID = ? " +
					"AND bpa.IsActive = 'Y' " +
					"AND bpa.IsACH = 'Y' " +
					"GROUP BY bpa.C_BPartner_ID, bpa.A_Ident_SSN, bpa.A_Name, bpb.SwiftCode, bpa.A_Email";
		else
			sql = "SELECT AccountNo, A_Ident_SSN, A_Name, bpb.SwiftCode , bpa.A_Email, bpb.RoutingNo " +
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
				bp[BPA_BANKROUTE] = rs.getString(6);
				if (bp[BPA_BANKROUTE] == null)
					bp[BPA_BANKROUTE] = "0116";
			} else {
				bp[BPA_A_ACCOUNT] 	= "NO CUENTA";
				bp[BPA_A_IDENT_SSN] = "NO RIF/CI";
				bp[BPA_A_NAME] 		= "NO NOMBRE";
				bp[BPA_SWIFTCODE] 	= "NO SWIFT";
				bp[BPA_A_EMAIL] 	= "";
				bp[BPA_BANKROUTE] 	= "0116";
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
		//	Process Bank Routing No
		String bankRoutingNo = bp[BPA_BANKROUTE];
		bankRoutingNo = String.format("%0"+ 4 +"d",Integer.parseInt(bankRoutingNo));
		bp[BPA_BANKROUTE] = bankRoutingNo; 
		
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
		return "BOD-" + creationDate ;
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
