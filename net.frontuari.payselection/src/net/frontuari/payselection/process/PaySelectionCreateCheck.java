/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
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
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package net.frontuari.payselection.process;

import java.util.ArrayList;
import java.util.logging.Level;

import org.compiere.model.MBPBankAccount;
import org.compiere.model.MBPartner;
import org.compiere.model.MOrder;
import org.compiere.model.MPaySelection;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.model.MPaySelectionLine;
import org.compiere.model.X_C_Order;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.AdempiereUserError;

import net.frontuari.payselection.base.FTUProcess;

public class PaySelectionCreateCheck extends FTUProcess {

	/**	Target Payment Rule			*/
	private String		p_PaymentRule = null;
	/**	Payment Selection			*/
	private int			p_C_PaySelection_ID = 0;
	/** one payment per invoice */
	private boolean							p_onepaymentPerInvoice	= false;
	/** The checks					*/
	private ArrayList<MPaySelectionCheck>	m_list = new ArrayList<MPaySelectionCheck>();
	
	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals("PaymentRule"))
				p_PaymentRule = (String)para[i].getParameter();
			else if (name.equalsIgnoreCase(MPaySelection.COLUMNNAME_IsOnePaymentPerInvoice))
				p_onepaymentPerInvoice = para[i].getParameterAsBoolean();
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
		p_C_PaySelection_ID = getRecord_ID();
		if (p_PaymentRule != null && p_PaymentRule.equals(X_C_Order.PAYMENTRULE_DirectDebit))
			p_PaymentRule = null;
	}

	@Override
	protected String doIt() throws Exception {
			
		if (log.isLoggable(Level.INFO)) log.info ("C_PaySelection_ID=" + p_C_PaySelection_ID
			+ ", PaymentRule=" + p_PaymentRule);
		
		MPaySelection psel = new MPaySelection (getCtx(), p_C_PaySelection_ID, get_TrxName());
		if (psel.get_ID() == 0)
			throw new IllegalArgumentException("Not found C_PaySelection_ID=" + p_C_PaySelection_ID);
		if (psel.isProcessed())
			throw new IllegalArgumentException("@Processed@");
		//
		MPaySelectionLine[] lines = psel.getLines(false);
		for (int i = 0; i < lines.length; i++)
		{
			MPaySelectionLine line = lines[i];
			if (!line.isActive() || line.isProcessed())
				continue;
			createCheck (line);
		}
		//
		psel.setProcessed(true);
		psel.saveEx();
		
		StringBuilder msgreturn = new StringBuilder("@C_PaySelectionCheck_ID@ - #").append(m_list.size());
		return msgreturn.toString();
	}
	
	/**
	 * 	Create Check from line
	 *	@param line
	 *	@throws Exception for invalid bank accounts
	 */
	private void createCheck (MPaySelectionLine line) throws Exception
	{
		if (!p_onepaymentPerInvoice)
		{
			// Try to find one
			for (int i = 0; i < m_list.size(); i++)
			{
				MPaySelectionCheck check = (MPaySelectionCheck) m_list.get(i);
				// Add to existing
				int bpartnerID = 0;
				if(line.get_ValueAsInt("C_Order_ID") > 0)
				{
					MOrder ord = new MOrder(getCtx(), line.get_ValueAsInt("C_Order_ID"), get_TrxName());
					bpartnerID = ord.getC_BPartner_ID();
				}
				else
				{
					bpartnerID = line.getC_Invoice().getC_BPartner_ID();
				}
				
				if (check.getC_BPartner_ID() == bpartnerID)
				{
					//	Add Line
					if (check.getC_BPartner_ID() != bpartnerID)
						throw new IllegalArgumentException("Line for different BPartner");
					
					if (check.isReceipt() == line.isSOTrx())
					{
						check.setPayAmt (check.getPayAmt().add(line.getPayAmt()));
						check.setDiscountAmt(check.getDiscountAmt().add(line.getDiscountAmt()));
						check.setWriteOffAmt(check.getWriteOffAmt().add(line.getWriteOffAmt()));
					}
					else
					{
						check.setPayAmt (check.getPayAmt().subtract(line.getPayAmt()));
						check.setDiscountAmt(check.getDiscountAmt().subtract(line.getDiscountAmt()));
						check.setWriteOffAmt(check.getWriteOffAmt().subtract(line.getWriteOffAmt()));
					}
					check.setQty (check.getQty()+1);
					if (!check.save())
						throw new IllegalStateException("Cannot save MPaySelectionCheck");
					line.setC_PaySelectionCheck_ID(check.getC_PaySelectionCheck_ID());
					line.setProcessed(true);
					if (!line.save())
						throw new IllegalStateException("Cannot save MPaySelectionLine");
					return;
				}
			}
		}
		//	Create new
		String PaymentRule = line.getPaymentRule();
		if (p_PaymentRule != null)
		{
			if (!X_C_Order.PAYMENTRULE_DirectDebit.equals(PaymentRule))
				PaymentRule = p_PaymentRule;
		}
		MPaySelectionCheck check = new MPaySelectionCheck(getCtx(), 0, get_TrxName());
		check.setAD_Org_ID(line.getAD_Org_ID());
		check.setC_PaySelection_ID (line.getC_PaySelection_ID());
		int C_BPartner_ID = 0;
		if(line.get_ValueAsInt("C_Order_ID") > 0)
		{
			MOrder ord = new MOrder(getCtx(), line.get_ValueAsInt("C_Order_ID"), get_TrxName());
			C_BPartner_ID = ord.getC_BPartner_ID();
		}
		else if(line.getC_Invoice_ID() > 0)
		{
			C_BPartner_ID = line.getInvoice().getC_BPartner_ID();
		}
		else
		{
			C_BPartner_ID = line.get_ValueAsInt("C_BPartner_ID");
		}
		check.setC_BPartner_ID (C_BPartner_ID);
		//
		if (X_C_Order.PAYMENTRULE_DirectDebit.equals(PaymentRule))
		{
			MBPBankAccount[] bas = MBPBankAccount.getOfBPartner (line.getCtx(), C_BPartner_ID); 
			for (int i = 0; i < bas.length; i++) 
			{
				MBPBankAccount account = bas[i];
				if (account.isDirectDebit())
				{
					check.setC_BP_BankAccount_ID(account.getC_BP_BankAccount_ID());
					break;
				}
			}
		}
		else if (X_C_Order.PAYMENTRULE_DirectDeposit.equals(PaymentRule))
		{
			MBPBankAccount[] bas = MBPBankAccount.getOfBPartner (line.getCtx(), C_BPartner_ID); 
			for (int i = 0; i < bas.length; i++) 
			{
				MBPBankAccount account = bas[i];
				if (account.isDirectDeposit())
				{
					check.setC_BP_BankAccount_ID(account.getC_BP_BankAccount_ID());
					break;
				}
			}
		}
		check.setPaymentRule (PaymentRule);
		//
		check.setIsReceipt(line.isSOTrx());
		check.setPayAmt (line.getPayAmt());
		check.setDiscountAmt(line.getDiscountAmt());
		check.setWriteOffAmt(line.getWriteOffAmt());
		check.setQty (1);
		if (!check.isValid())
		{
			MBPartner bp = MBPartner.get(getCtx(), C_BPartner_ID);
			StringBuilder msg = new StringBuilder("@NotFound@ @C_BP_BankAccount@: ").append(bp.getName());
			throw new AdempiereUserError(msg.toString());
		}
		if (!check.save())
			throw new IllegalStateException("Cannot save MPaySelectionCheck");
		line.setC_PaySelectionCheck_ID(check.getC_PaySelectionCheck_ID());
		line.setProcessed(true);
		if (!line.save())
			throw new IllegalStateException("Cannot save MPaySelectionLine");
		m_list.add(check);
	}	//	createCheck

}
