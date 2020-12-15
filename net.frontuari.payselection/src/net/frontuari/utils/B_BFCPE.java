package net.frontuari.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.PaymentExport;

/**
 * 
 * @author Argenis Rodríguez
 *
 */
public class B_BFCPE implements PaymentExport {
	
	private static CLogger s_log = CLogger.getCLogger(B_BFCPE.class);
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
	
	/**Column Name Affiliate Code*/
	private static final String COLUMNNAME_AffiliateCode = "AffiliateCode";
	/**Column Name Document No*/
	private static String COLUMNNAME_DocumentNo = "DocumentNo";
	
	@Override
	public int exportToFile(MPaySelectionCheck[] checks, File file, StringBuffer err) {
		
		if (checks == null || checks.length == 0)
			return 0;
		
		try
		{
			if (file.exists())
				file.delete();
		}
		catch (Exception e)
		{
			s_log.log(Level.WARNING, "Could not delete - " + file.getAbsolutePath(), e);
		}
		
		int C_PaySelection_ID = checks[0].getC_PaySelection_ID();
		MPaySelection paySelection = new MPaySelection(checks[0].getCtx(), C_PaySelection_ID, checks[0].get_TrxName());
		MBankAccount ba = new MBankAccount(paySelection.getCtx(), paySelection.getC_BankAccount_ID(), paySelection.get_TrxName());
		MBank bank = new MBank(ba.getCtx(), ba.getC_Bank_ID(), ba.get_TrxName());
		MOrgInfo oInfo = MOrgInfo.get(paySelection.getCtx(), paySelection.getAD_Org_ID(), paySelection.get_TrxName());
		//Get Parent Org
		MOrg pOrg = new MOrg(paySelection.getCtx(), oInfo.getParent_Org_ID(), paySelection.get_TrxName());
		
		String taxId = Optional.ofNullable(oInfo.getTaxID())
				.orElse("").replace("-", "");
		
		taxId = taxId.length() > 10 ? taxId.substring(0, 10) : taxId;
		
		String affiliateCode = Optional.ofNullable(bank.get_ValueAsString(COLUMNNAME_AffiliateCode))
				.orElse("");
		affiliateCode = affiliateCode.length() > 6 ? affiliateCode.substring(0, 6) : affiliateCode;
		
		String accountNo = ba.getAccountNo();
		accountNo = accountNo.length() > 20 ? accountNo.substring(0, 20) : accountNo;
		
		String documentNo = Optional.ofNullable(paySelection.get_ValueAsString(COLUMNNAME_DocumentNo))
				.orElse("").trim();
		
		documentNo = extractNumber(documentNo);
		documentNo = documentNo.length() > 12 ? documentNo.substring(0, 12) : documentNo;
		
		int noLines = 0;
		StringBuffer line = new StringBuffer();
		FileWriter fw = null;
		try {
			fw = new FileWriter(file);
			Timestamp today = new Timestamp(System.currentTimeMillis());
			SimpleDateFormat formatDate = new SimpleDateFormat("yyyyMMdd");
			SimpleDateFormat formatHour = new SimpleDateFormat("hhmmss");
			Timestamp payDate = paySelection.getPayDate();
			
			String strDate = formatDate.format(today);
			String strHour = formatHour.format(today);
			String strPayDate = formatDate.format(payDate);
			
			//Add Header Register
			line.append("000000")
				.append(String.format("%-8s", strDate))
				.append(String.format("%-6s", strHour))
				.append(String.format("%-8s", strPayDate))
				.append(String.format("%6s", "").replace(" ", "0"))
				.append(String.format("%8s", "").replace(" ", "0"))
				.append(String.format("%6s", "").replace(" ", "0"))
				.append(String.format("%6s", affiliateCode).replace(" ", "0"))
				.append(String.format("%6s", 77).replace(" ", "0"))
				.append(String.format("%22s", accountNo).replace(" ", "0"))
				.append(String.format("%3s", ""))
				.append(String.format("%22s", "").replace(" ", "0"))
				.append(String.format("%12s", documentNo).replace(" ", "0"))
				.append(String.format("%10s", taxId))
				.append(String.format("%98s", "").replace(" ", "0"));
			fw.write(line.toString());
			noLines++;
			//End of Header Register
			
			BigDecimal totalAmt = BigDecimal.ZERO;
			//Write Lines
			for (int i = 0; i < checks.length; i++)
			{
				MPaySelectionCheck check = checks[i];
				documentNo = check.getDocumentNo();
				documentNo = extractNumber(documentNo);
				
				documentNo = documentNo.length() > 10 ? documentNo.substring(0, 10) : documentNo;
				
				String seq = String.valueOf(i + 1);
				
				String bp[] = getBPartnerInfo(check.getC_BPartner_ID(), check);
				BigDecimal amtBD = check.getPayAmt();
				
				if (amtBD.scale() > 2)
					amtBD = amtBD.setScale(2, RoundingMode.HALF_UP);
				
				totalAmt = totalAmt.add(amtBD);
				
				String amt = String.format("%.2f", amtBD).replace(".", "").replace(",", "");
				amt = String.format("%15s", amt).replace(" ", "0");
				
				//Write Detail Register
				line = new StringBuffer();
				line
					.append(Env.NL)
					.append(String.format("%6s", seq).replace(" ", "0"))
					.append(bp[BPA_A_ACCOUNT])
					.append(bp[BPA_A_IDENT_SSN])
					.append(String.format("%5s", "").replace(" ", "0"))
					.append(String.format("%5s", "").replace(" ", "0"))
					.append(String.format("%10s", documentNo).replace(" ", "0"))
					.append(amt)
					.append("C")
					.append("0")
					.append(String.format("%-40s", bp[BPA_A_NAME]))
					.append("0")
					.append("000")
					.append(String.format("%-58s", bp[BPA_A_EMAIL]))
					.append("08");
				fw.write(line.toString());
				noLines++;
				//End Detail Register
			}
			
			String parentOrgName = pOrg.getName();
			parentOrgName = replaceSpecialCharacters(parentOrgName);
			parentOrgName = parentOrgName.length() > 40 ? parentOrgName.substring(0, 40)
					: parentOrgName;
			
			String totalAmtStr = String.format("%.2f", totalAmt).replace(".", "").replace(",", "");
			totalAmtStr = String.format("%15s", totalAmtStr).replace(" ", "0");
			
			//Write Total Register
			line = new StringBuffer();
			line
				.append(Env.NL)
				.append(String.format("%6s", "").replace(" ", "9"))
				.append(String.format("%-40s", parentOrgName))
				.append(String.format("%6s", String.valueOf(checks.length)).replace(" ", "0"))
				.append(totalAmtStr)
				.append(totalAmtStr)
				.append(String.format("%6s", "1").replace(" ", "0"))
				.append(String.format("%6s", String.valueOf(checks.length)).replace(" ", "0"))
				.append(String.format("%76s", "").replace(" ", "0"));
			//End Total Register
		} catch (Exception e) {
			err.append(e.toString());
			s_log.log(Level.SEVERE, "", e);
			return -1;
		} finally {
			closeFileWriter(fw);
		}
		
		return noLines;
	}
	
	/**
	 * @author Argenis Rodríguez
	 * @param str
	 * @return
	 */
	private static String extractNumber(String str) {
		
		String regExp = "[0-9]+\\.?[0-9]*";
		Pattern pattern = Pattern.compile(regExp);
		Matcher matcher = pattern.matcher(str);
		StringBuffer retVal = new StringBuffer();
		
		while (matcher.find())
			retVal.append(matcher.group());
		
		return retVal.toString();
	}
	
	/**
	 * @author Argenis Rodríguez
	 * @param fw
	 */
	private void closeFileWriter(FileWriter fw) {
		try {
			fw.flush();
			fw.close();
		} catch (IOException e) {
			s_log.log(Level.SEVERE, "", e);
		}
	}
	
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
			if(bpaTaxID.length()<10){
				bpaTaxID = String.format("%0"+ 10 +"d",Integer.parseInt(bpaTaxID));
			}
			//	Join Letter with Number
			bpaTaxID = letterTaxID+bpaTaxID;
			bpaTaxID = bpaTaxID.substring(0, bpaTaxID.length() >= 11? 11: bpaTaxID.length());
			bpaTaxID = String.format("%1$-" + 11 + "s", bpaTaxID);
		}
		bp[BPA_A_IDENT_SSN] = bpaTaxID;
		//	Process Account Name
		//	Using Method replaceSpecialCharacters with value of bp[BPA_A_NAME]
		String bpaName = replaceSpecialCharacters(bp[BPA_A_NAME]);
		//	End Jorge Colmenarez
		bpaName = bpaName.substring(0, bpaName.length() >= 40? 40: bpaName.length());
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
		String [] original = {"á", "à", "ä", "é", "è", "ë", "í", "ì", "ï", "ó", "ò", "ö", "ú"
				, "ù", "u", "ñ", "Á", "À", "Ä", "É", "È", "Ë", "Í", "Ì", "Ï", "Ó", "Ò", "Ö"
				, "Ú", "Ù", "Ü", "Ñ", "ç", "Ç", ".", ","};
	    // ASCII character string that will replace the original.
		String [] ascii = {"a", "a", "a", "e", "e", "e", "i", "i", "i", "o", "o", "o", "u"
				, "u", "u", "n", "A", "A", "A", "E", "E", "E", "I", "I", "I", "O", "O", "O"
				, "U", "U", "U", "N", "c", "C", "", ""};
	    String output = input;
	    for (int i=0; i<original.length; i++) {
	        // Reemplazamos los caracteres especiales.
	        output = output.replace(original[i], ascii[i]);
	    }//for i
	    return output;
	}//replaceSpecialCharacters
}
