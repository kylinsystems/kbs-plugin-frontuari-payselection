package net.frontuari.payselection.process;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.model.MPaySelectionLine;
import org.compiere.model.MPayment;
import org.compiere.model.MPaymentBatch;
import org.compiere.util.DB;

import net.frontuari.payselection.base.FTUProcess;
import net.frontuari.payselection.model.MFTUPaymentRequestLine;

public class VoidPaymentBatch extends FTUProcess {

	@Override
	protected void prepare() {
	}

	@Override
	protected String doIt() throws Exception {
		int counter = 0;
		
		for(MPayment pay : getPayments(getRecord_ID()))
		{
			if(!pay.processIt(MPayment.ACTION_Reverse_Correct))
			{
				throw new AdempiereException(pay.getProcessMsg());
			}
			pay.saveEx(get_TrxName());
			//	Void PaySelectionCheck
			MPaySelectionCheck psc = MPaySelectionCheck.getOfPayment(getCtx(), pay.get_ID(), get_TrxName());
			if(psc != null)
			{
				psc.setIsActive(false);
				psc.saveEx(get_TrxName());
				//	Void PaySelectionLines
				for(MPaySelectionLine psl : psc.getPaySelectionLines(true))
				{
					psl.setIsActive(false);
					psl.saveEx(get_TrxName());
					if(psl.get_ValueAsInt("FTU_PaymentRequest_ID") > 0)
					{
						MFTUPaymentRequestLine prl = new MFTUPaymentRequestLine(getCtx(), psl.get_ValueAsInt("FTU_PaymentRequest_ID"), get_TrxName());
						prl.setIsPrepared(false);
						prl.saveEx(get_TrxName());
					}
				}
				//	Void PaySelection
				MPaySelection ps = psc.getParent();
				ps.setIsActive(false);
				ps.saveEx(get_TrxName());
			}
			MPaymentBatch pb = new MPaymentBatch(getCtx(), getRecord_ID(), get_TrxName());
			pb.setIsActive(false);
			pb.saveEx(get_TrxName());
			counter++;
		}
		
		
		
		return "@Voided@: #"+counter+" @C_Payment_ID@";
	}
	
	/**************************************************************************
	 * 	Get Payment Batch Payments
	 *	@return Array of lines
	 */
	public MPayment[] getPayments(int PaymentBatch_ID)
	{
		ArrayList<MPayment> list = new ArrayList<MPayment>();
		String sql = "SELECT * FROM C_Payment WHERE DocStatus = 'CO' AND C_PaymentBatch_ID=? ORDER BY DocumentNo";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, get_TrxName());
			pstmt.setInt(1, PaymentBatch_ID);
			rs = pstmt.executeQuery();
			while (rs.next())
				list.add(new MPayment (getCtx(), rs, get_TrxName()));
		}
		catch (SQLException ex)
		{
			log.log(Level.SEVERE, sql, ex);
		}
		finally
		{
			DB.close(rs, pstmt);
		}
		
		//
		MPayment[] retValue = new MPayment[list.size()];
		list.toArray(retValue);
		return retValue;
	}	//	getPayments

}
