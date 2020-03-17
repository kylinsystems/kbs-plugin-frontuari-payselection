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
package net.frontuari.utils;

import java.io.File;
import java.io.FileWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.logging.Level;

import org.compiere.model.MBankAccount;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.model.MPaySelectionLine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.PaymentExport;

/**
 * @author <a href="mailto:yamelsenih@gmail.com">Yamel Senih</a>
 * Export class for Banplus Bank
 */
public class B_BanplusPE implements PaymentExport {

	/** Logger								*/
	static private CLogger	s_log = CLogger.getCLogger (B_BanplusPE.class);

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
		
		//	
		MPaySelection m_PaySelection = (MPaySelection) checks[0].getC_PaySelection();
		MBankAccount m_BankAccount = (MBankAccount) m_PaySelection.getC_BankAccount();
		MOrgInfo orgInfo = MOrgInfo.get(m_PaySelection.getCtx(), m_PaySelection.getAD_Org_ID(), m_PaySelection.get_TrxName());
		
		//	Payments Generated
		int payGenerated = checks.length;
		
		//	Process Organization Tax ID
		String orgTaxID = orgInfo.getTaxID().replace("-", "").trim();
		orgTaxID = orgTaxID.substring(0, (orgTaxID.length() >= 10? 10: orgTaxID.length()));
		//	Account No
		String bankAccountNo = m_BankAccount.getAccountNo().trim();
		bankAccountNo = bankAccountNo.substring(0, (bankAccountNo.length() >= 20? 20: bankAccountNo.length()));
		bankAccountNo = bankAccountNo.replace(" ", "");
		bankAccountNo = String.format("%1$-" + 20 + "s", bankAccountNo).replace(" ", "0");
		//	Batch Document No
		String batchDocNo = m_PaySelection.get_ValueAsString("DocumentNo").trim();
		//	Format Date
		String format = "yyyyMMdd";
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		
		String separator = ";";
		int noLines = 0;
		StringBuffer line = null;
		try
		{
			
			FileWriter fw = new FileWriter(file);
			
			//	Payment Amount
			String totalAmt = String.format("%.2f", m_PaySelection.getTotalAmt().abs()).replace(".", "").replace(",", "");
			
			//  write header
			line = new StringBuffer();
			//	Header
			line.append(orgTaxID)									//	Organization Tax ID
				.append(separator)									//	Separator
				.append(bankAccountNo)								//	Bank Account No
				.append(separator)									//	Separator
				.append(payGenerated)								//	Payments Generated
				.append(separator)									//	Separator
				.append(totalAmt)									//	Current Date
				.append(separator)									//	Separator
				.append(sdf.format(m_PaySelection.getPayDate()))	//	Payment Date
				.append(separator)									//	Separator
				.append(batchDocNo);								//	Document No
			//	
			fw.write(line.toString());
			noLines++;

			//  write lines
			for (int i = 0; i < checks.length; i++)
			{
				MPaySelectionCheck mpp = checks[i];
				if (mpp == null)
					continue;
				//  BPartner Info
				String bp[] = getBPartnerInfo(mpp.getC_BPartner_ID(),mpp);
				
				//  Comment - list of invoice document no
				String comment = new String();
				MPaySelectionLine[] psls = mpp.getPaySelectionLines(false);
				for (int l = 0; l < psls.length; l++)
				{
					if (l > 0)
						comment += " ";
					comment += psls[l].getInvoice().getDocumentNo();
				}
				
				//	Process Comment
				comment = comment.substring(0, (comment.length() >= 120? 120: comment.length()));
				comment = String.format("%1$-" + 120 + "s", comment);
				
				//	Payment Detail
				//	Process Document No
				String docNo = mpp.getDocumentNo();
				docNo = docNo.substring(0, (docNo.length() >= 8? 8: docNo.length()));
				//	Payment Amount
				String amt = String.format("%.2f", mpp.getPayAmt().abs()).replace(".", "").replace(",", "");
				//	Process Person Type
				String personType = bp[BPA_A_IDENT_SSN];
				personType = bp[BPA_A_IDENT_SSN].substring(0, 1);
				//	Evaluate
				if(personType.equals("V"))
					personType = "01";
				else if(personType.equals("E"))
					personType = "08";
				else
					personType = "04";
				//	Process Business Partner Tax ID
				String bpTaxID = bp[BPA_A_IDENT_SSN].substring(1, (comment.length() >= 10? 10: comment.length()));
				
				//	Line
				line = new StringBuffer();
				line
					//	Credit Detail
					//	New Line
					.append(Env.NL)
					.append(personType)									//	Business Partner Person Type
					.append(separator)									//	Separator
					.append(bpTaxID)									//	Business Partner Tax ID
					.append(separator)									//	Separator
					.append(bp[BPA_A_NAME])								//	Business Partner Name for Account
					.append(separator)									//	Separator
					.append(bp[BPA_A_ACCOUNT])							//	Account No
					.append(separator)									//	Separator
					.append(amt)										//	Payment Amount
					.append(separator)									//	Separator
					.append(docNo);										//	Document No
				fw.write(line.toString());
				noLines++;
			}   //  write line
			
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
	 * @author <a href="mailto:yamelsenih@gmail.com">Yamel Senih</a> 17/04/2013, 12:11:16
	 * @param C_BPartner_ID
	 * @return
	 * @return String[]
	 */
	private String[] getBPartnerInfo (int C_BPartner_ID,MPaySelectionCheck mpp)
	{
		String sql = null;
		String[] bp = new String[5];
		//	Sql
		if (mpp.getC_BP_BankAccount_ID()==0)
			sql = "SELECT MAX(bpa.AccountNo) AccountNo, bpa.A_Ident_SSN, bpa.A_Name, bpb.SwiftCode, bpa.A_Email " +
					"FROM C_BP_BankAccount bpa " +
					"INNER JOIN C_Bank bpb ON(bpb.C_Bank_ID = bpa.C_Bank_ID) " +
					"WHERE bpa.C_BPartner_ID = ? " +
					"AND bpa.IsActive = 'Y' " +
					"AND bpa.IsACH = 'Y' " +
					"GROUP BY bpa.C_BPartner_ID, bpa.A_Ident_SSN, bpa.A_Name, bpb.SwiftCode, bpa.A_Email ";
		else
			sql = "SELECT AccountNo, A_Ident_SSN, A_Name, bpb.SwiftCode , bpa.A_Email " +
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
	 * @author <a href="mailto:yamelsenih@gmail.com">Yamel Senih</a> 17/04/2013, 12:11:05
	 * @param bp
	 * @return
	 * @return String[]
	 */
	private String [] processBPartnerInfo(String [] bp){
		//	Process Business Partner Account No
		String bpaAccount = bp[BPA_A_ACCOUNT];
		bpaAccount = bpaAccount.substring(0, bpaAccount.length() >= 20? 20: bpaAccount.length());
		bpaAccount = String.format("%1$-" + 20 + "s", bpaAccount);
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
			bpaTaxID = bpaTaxID.substring(0, bpaTaxID.length() >= 10? 10: bpaTaxID.length());
			bpaTaxID = String.format("%1$-" + 10 + "s", bpaTaxID);
		}
		bp[BPA_A_IDENT_SSN] = bpaTaxID;
		//	Process Account Name
		//	Using Method replaceSpecialCharacters with value of bp[BPA_A_NAME]
		String bpaName = replaceSpecialCharacters(bp[BPA_A_NAME]);
		//	End Jorge Colmenarez
		bpaName = bpaName.substring(0, bpaName.length() >= 100? 100: bpaName.length());
		bp[BPA_A_NAME] = bpaName;
		//	Process Swift Code
		String bpaSwiftCode = bp[BPA_SWIFTCODE];
		bpaSwiftCode = bpaSwiftCode.substring(0, bpaSwiftCode.length() >= 12? 12: bpaSwiftCode.length());
		
		bp[BPA_SWIFTCODE] = bpaSwiftCode;
		//	Process e-mail
		String bpaEmail = bp[BPA_A_EMAIL];
		bpaName = bpaEmail.substring(0, bpaEmail.length() >= 60? 60: bpaEmail.length());
		bpaEmail = String.format("%1$-" + 60 + "s", bpaEmail);
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
		return "Banplus-" + creationDate ;
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
