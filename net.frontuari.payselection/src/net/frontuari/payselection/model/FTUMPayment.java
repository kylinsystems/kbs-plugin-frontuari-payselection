package net.frontuari.payselection.model;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.PaymentUtil;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.MBPartner;
import org.compiere.model.MBankAccount;
import org.compiere.model.MCash;
import org.compiere.model.MCashLine;
import org.compiere.model.MClient;
import org.compiere.model.MConversionRate;
import org.compiere.model.MConversionRateUtil;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MOrder;
import org.compiere.model.MPayment;
import org.compiere.model.MPaymentAllocate;
import org.compiere.model.MSysConfig;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.X_C_CashLine;
import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.IBAN;
import org.compiere.util.Msg;
import org.compiere.util.Util;

public class FTUMPayment extends MPayment {

	/**
	 * 
	 */
	private static final long serialVersionUID = 462183857061524348L;

	public FTUMPayment(Properties ctx, int C_Payment_ID, String trxName) {
		super(ctx, C_Payment_ID, trxName);
	}
	
	public FTUMPayment(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}
	
	/**************************************************************************
	 * 	Process document
	 *	@param processAction document action
	 *	@return true if performed
	 */
	public boolean processIt (String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}	//	process

	/**************************************************************************
	 * 	Complete Document
	 * 	@return new status (Complete, In Progress, Invalid, Waiting ..)
	 */
	public String completeIt()
	{
		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			m_justPrepared = false;
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}

		// Set the definite document number after completed (if needed)
		setDefiniteDocumentNo();

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		//	Implicit Approval
		if (!isApproved())
			approveIt();
		if (log.isLoggable(Level.INFO)) log.info(toString());

		//	Charge Handling
		boolean createdAllocationRecords = false;
		if (getC_Charge_ID() != 0)
		{
			setIsAllocated(true);
		}
		else
		{
			createdAllocationRecords = allocateIt();	//	Create Allocation Records
			testAllocation();
		}

		//	Project update
		if (getC_Project_ID() != 0)
		{
		//	MProject project = new MProject(getCtx(), getC_Project_ID());
		}
		//	Update BP for Prepayments
		if (getC_BPartner_ID() != 0 && getC_Invoice_ID() == 0 && getC_Charge_ID() == 0 && MPaymentAllocate.get(this).length == 0 && !createdAllocationRecords)
		{
			MBPartner bp = new MBPartner (getCtx(), getC_BPartner_ID(), get_TrxName());
			DB.getDatabase().forUpdate(bp, 0);
			//	Update total balance to include this payment 
			BigDecimal payAmt = MConversionRate.convertBase(getCtx(), getPayAmt(), 
				getC_Currency_ID(), getDateAcct(), getC_ConversionType_ID(), getAD_Client_ID(), getAD_Org_ID());
			if (payAmt == null)
			{
				m_processMsg = MConversionRateUtil.getErrorMessage(getCtx(), "ErrorConvertingCurrencyToBaseCurrency",
						getC_Currency_ID(), MClient.get(getCtx()).getC_Currency_ID(), getC_ConversionType_ID(), getDateAcct(), get_TrxName());
				return DocAction.STATUS_Invalid;
			}
			//	Total Balance
			BigDecimal newBalance = bp.getTotalOpenBalance();
			if (newBalance == null)
				newBalance = Env.ZERO;
			if (isReceipt())
				newBalance = newBalance.subtract(payAmt);
			else
				newBalance = newBalance.add(payAmt);
				
			bp.setTotalOpenBalance(newBalance);
			bp.setSOCreditStatus();
			bp.saveEx();
		}		

		//	Counter Doc
		MPayment counter = createCounterDoc();
		if (counter != null)
			m_processMsg += " @CounterDoc@: @C_Payment_ID@=" + counter.getDocumentNo();

		// @Trifon - CashPayments
		//if ( getTenderType().equals("X") ) {
		if ( isCashbookTrx()) {
			// Create Cash Book entry
			if ( getC_CashBook_ID() <= 0 ) {
				log.saveError("Error", Msg.parseTranslation(getCtx(), "@Mandatory@: @C_CashBook_ID@"));
				m_processMsg = "@NoCashBook@";
				return DocAction.STATUS_Invalid;
			}
			MCash cash = MCash.get (getCtx(), getAD_Org_ID(), getDateAcct(), getC_Currency_ID(), get_TrxName());
			if (cash == null || cash.get_ID() == 0)
			{
				m_processMsg = "@NoCashBook@";
				return DocAction.STATUS_Invalid;
			}
			MCashLine cl = new MCashLine( cash );
			cl.setCashType( X_C_CashLine.CASHTYPE_GeneralReceipts );
			cl.setDescription("Generated From Payment #" + getDocumentNo());
			cl.setC_Currency_ID( this.getC_Currency_ID() );
			cl.setC_Payment_ID( getC_Payment_ID() ); // Set Reference to payment.
			StringBuilder info=new StringBuilder();
			info.append("Cash journal ( ")
				.append(cash.getDocumentNo()).append(" )");				
			m_processMsg = info.toString();
			//	Amount
			BigDecimal amt = this.getPayAmt();
/*
			MDocType dt = MDocType.get(getCtx(), invoice.getC_DocType_ID());			
			if (MDocType.DOCBASETYPE_APInvoice.equals( dt.getDocBaseType() )
				|| MDocType.DOCBASETYPE_ARCreditMemo.equals( dt.getDocBaseType() ) 
			) {
				amt = amt.negate();
			}
*/
			cl.setAmount( amt );
			//
			cl.setDiscountAmt( Env.ZERO );
			cl.setWriteOffAmt( Env.ZERO );
			cl.setIsGenerated( true );
			
			if (!cl.save(get_TrxName()))
			{
				m_processMsg = "Could not save Cash Journal Line";
				return DocAction.STATUS_Invalid;
			}
		}
		// End Trifon - CashPayments
		
		//	update C_Invoice.C_Payment_ID and C_Order.C_Payment_ID reference
		if (getC_Invoice_ID() != 0)
		{
			MInvoice inv = new MInvoice(getCtx(), getC_Invoice_ID(), get_TrxName());
			if (inv.getC_Payment_ID() != getC_Payment_ID())
			{
				inv.setC_Payment_ID(getC_Payment_ID());
				inv.saveEx();
			}
		}		
		if (getC_Order_ID() != 0)
		{
			MOrder ord = new MOrder(getCtx(), getC_Order_ID(), get_TrxName());
			if (ord.getC_Payment_ID() != getC_Payment_ID())
			{
				ord.setC_Payment_ID(getC_Payment_ID());
				ord.saveEx();
			}
		}
		
		//	User Validation
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}

		//
		setProcessed(true);
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}	//	completeIt
	
	/**
	 * 	Allocate It.
	 * 	Only call when there is NO allocation as it will create duplicates.
	 * 	If an invoice exists, it allocates that 
	 * 	otherwise it allocates Payment Selection.
	 *	@return true if allocated
	 */
	public boolean allocateIt()
	{
		//	Create invoice Allocation -	See also MCash.completeIt
		if (getC_Invoice_ID() != 0)
		{	
				return allocateInvoice();
		}	
		
		if (getC_Order_ID() != 0)
			return false;
		
		if(isPrepayment())
			return false;
		
		//	Invoices of a AP Payment Selection
		if (allocatePaySelection())
			return true;
			
		//	Allocate to multiple Payments based on entry
		MPaymentAllocate[] pAllocs = MPaymentAllocate.get(this);
		if (pAllocs.length == 0)
			return false;
		
		MAllocationHdr alloc = new MAllocationHdr(getCtx(), false, 
			getDateTrx(), getC_Currency_ID(), 
				Msg.translate(getCtx(), "C_Payment_ID")	+ ": " + getDocumentNo(), 
				get_TrxName());
		alloc.setAD_Org_ID(getAD_Org_ID());
		alloc.setDateAcct(getDateAcct()); // in case date acct is different from datetrx in payment; IDEMPIERE-1532 tbayen
		if (!alloc.save())
		{
			log.severe("P.Allocations not created");
			return false;
		}
		//	Lines
		for (int i = 0; i < pAllocs.length; i++)
		{
			MPaymentAllocate pa = pAllocs[i];

			BigDecimal allocationAmt = pa.getAmount();			//	underpayment
			if (pa.getOverUnderAmt().signum() < 0 && pa.getAmount().signum() > 0)
				allocationAmt = allocationAmt.add(pa.getOverUnderAmt());	//	overpayment (negative)

			MAllocationLine aLine = null;
			if (isReceipt())
				aLine = new MAllocationLine (alloc, allocationAmt,
					pa.getDiscountAmt(), pa.getWriteOffAmt(), pa.getOverUnderAmt());
			else
				aLine = new MAllocationLine (alloc, allocationAmt.negate(),
					pa.getDiscountAmt().negate(), pa.getWriteOffAmt().negate(), pa.getOverUnderAmt().negate());
			aLine.setDocInfo(pa.getC_BPartner_ID(), 0, pa.getC_Invoice_ID());
			aLine.setPaymentInfo(getC_Payment_ID(), 0);
			if (!aLine.save(get_TrxName()))
				log.warning("P.Allocations - line not saved");
			else
			{
				pa.setC_AllocationLine_ID(aLine.getC_AllocationLine_ID());
				pa.saveEx();
			}
		}
		// added AdempiereException by zuhri
		if (!alloc.processIt(DocAction.ACTION_Complete))
			throw new AdempiereException(Msg.getMsg(getCtx(), "FailedProcessingDocument") + " - " + alloc.getProcessMsg());
		addDocsPostProcess(alloc);
		// end added
		m_processMsg = "@C_AllocationHdr_ID@: " + alloc.getDocumentNo();
		return alloc.save(get_TrxName());
	}	//	allocateIt

	/**
	 * 	Allocate single AP/AR Invoice
	 * 	@return true if allocated
	 */
	protected boolean allocateInvoice()
	{
		//	calculate actual allocation
		BigDecimal allocationAmt = getPayAmt();			//	underpayment
		if (getOverUnderAmt().signum() < 0 && getPayAmt().signum() > 0)
			allocationAmt = allocationAmt.add(getOverUnderAmt());	//	overpayment (negative)
		
		MDocType docType = (MDocType)getC_DocType();
		int C_DocTypeAllocation_ID = docType.get_ValueAsInt("C_DocTypeAllocation_ID");

		MAllocationHdr alloc = new MAllocationHdr(getCtx(), false, 
			getDateTrx(), getC_Currency_ID(),
			Msg.translate(getCtx(), "C_Payment_ID") + ": " + getDocumentNo() + " [1]", get_TrxName());
		alloc.setAD_Org_ID(getAD_Org_ID());
		alloc.setDateAcct(getDateAcct()); // in case date acct is different from datetrx in payment
		if(C_DocTypeAllocation_ID>0)
			alloc.setC_DocType_ID(C_DocTypeAllocation_ID);
		alloc.saveEx();
		MAllocationLine aLine = null;
		if (isReceipt())
			aLine = new MAllocationLine (alloc, allocationAmt, 
				getDiscountAmt(), getWriteOffAmt(), getOverUnderAmt());
		else
			aLine = new MAllocationLine (alloc, allocationAmt.negate(), 
				getDiscountAmt().negate(), getWriteOffAmt().negate(), getOverUnderAmt().negate());
		aLine.setDocInfo(getC_BPartner_ID(), 0, getC_Invoice_ID());
		aLine.setC_Payment_ID(getC_Payment_ID());
		aLine.saveEx(get_TrxName());
		// added AdempiereException by zuhri
		if (!alloc.processIt(DocAction.ACTION_Complete))
			throw new AdempiereException(Msg.getMsg(getCtx(), "FailedProcessingDocument") + " - " + alloc.getProcessMsg());
		addDocsPostProcess(alloc);
		// end added
		alloc.saveEx(get_TrxName());
		m_justCreatedAllocInv = alloc;
		m_processMsg = "@C_AllocationHdr_ID@: " + alloc.getDocumentNo();
			
		//	Get Project from Invoice
		int C_Project_ID = DB.getSQLValue(get_TrxName(), 
			"SELECT MAX(C_Project_ID) FROM C_Invoice WHERE C_Invoice_ID=?", getC_Invoice_ID());
		if (C_Project_ID > 0 && getC_Project_ID() == 0)
			setC_Project_ID(C_Project_ID);
		else if (C_Project_ID > 0 && getC_Project_ID() > 0 && C_Project_ID != getC_Project_ID())
			log.warning("Invoice C_Project_ID=" + C_Project_ID 
				+ " <> Payment C_Project_ID=" + getC_Project_ID());
		return true;
	}	//	allocateInvoice
	
	/**
	 * 	Allocate Payment Selection
	 * 	@return true if allocated
	 */
	protected boolean allocatePaySelection()
	{
		MAllocationHdr alloc = new MAllocationHdr(getCtx(), false, 
			getDateTrx(), getC_Currency_ID(),
			Msg.translate(getCtx(), "C_Payment_ID")	+ ": " + getDocumentNo() + " [n]", get_TrxName());
		alloc.setAD_Org_ID(getAD_Org_ID());
		alloc.setDateAcct(getDateAcct()); // in case date acct is different from datetrx in payment
		
		String sql = "SELECT psc.C_BPartner_ID, psl.C_Invoice_ID, psl.IsSOTrx, "	//	1..3
			+ " psl.PayAmt, psl.DiscountAmt, psl.DifferenceAmt, psl.OpenAmt, psl.WriteOffAmt, "  // 4..8
			+ " psl.C_Order_ID, psl.IsManual "	// 9..10
			+ "FROM C_PaySelectionLine psl"
			+ " INNER JOIN C_PaySelectionCheck psc ON (psl.C_PaySelectionCheck_ID=psc.C_PaySelectionCheck_ID) "
			+ "WHERE psc.C_Payment_ID=?";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, get_TrxName());
			pstmt.setInt(1, getC_Payment_ID());
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				int C_BPartner_ID = rs.getInt(1);
				int C_Invoice_ID = rs.getInt(2);
				int C_Order_ID = rs.getInt(9);
				boolean isManual = rs.getBoolean(10);
				if (C_BPartner_ID == 0 && C_Invoice_ID == 0)
					continue;
				if (C_BPartner_ID == 0 && C_Order_ID == 0)
					continue;
				if (C_BPartner_ID != 0 && C_Invoice_ID == 0 && C_Order_ID == 0 && isManual)
					continue;
				boolean isSOTrx = "Y".equals(rs.getString(3));
				BigDecimal PayAmt = rs.getBigDecimal(4);
				BigDecimal DiscountAmt = rs.getBigDecimal(5);
				BigDecimal WriteOffAmt = rs.getBigDecimal(8);
				BigDecimal OpenAmt = rs.getBigDecimal(7);
				BigDecimal OverUnderAmt = OpenAmt.subtract(PayAmt)
					.subtract(DiscountAmt).subtract(WriteOffAmt);
				//
				if (alloc.get_ID() == 0 && !alloc.save(get_TrxName()))
				{
					log.log(Level.SEVERE, "Could not create Allocation Hdr");
					return false;
				}
				MAllocationLine aLine = null;
				if (isSOTrx)
					aLine = new MAllocationLine (alloc, PayAmt, 
						DiscountAmt, WriteOffAmt, OverUnderAmt);
				else
					aLine = new MAllocationLine (alloc, PayAmt.negate(), 
						DiscountAmt.negate(), WriteOffAmt.negate(), OverUnderAmt.negate());
				aLine.setDocInfo(C_BPartner_ID, 0, C_Invoice_ID);
				aLine.setC_Payment_ID(getC_Payment_ID());
				if (!aLine.save(get_TrxName()))
					log.log(Level.SEVERE, "Could not create Allocation Line");
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, "allocatePaySelection", e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		
		//	Should start WF
		boolean ok = true;
		if (alloc.get_ID() == 0)
		{
			if (log.isLoggable(Level.FINE)) log.fine("No Allocation created - C_Payment_ID=" 
				+ getC_Payment_ID());
			ok = false;
		}
		else
		{
			// added Adempiere Exception by zuhri
			if (alloc.processIt(DocAction.ACTION_Complete)) {
				addDocsPostProcess(alloc);
				ok = alloc.save(get_TrxName());
			} else {
				throw new AdempiereException(Msg.getMsg(getCtx(), "FailedProcessingDocument") + " - " + alloc.getProcessMsg());
			}
			// end added by zuhri
			m_processMsg = "@C_AllocationHdr_ID@: " + alloc.getDocumentNo();
		}
		return ok;
	}	//	allocatePaySelection
	
	/**
	 * 	Before Save
	 *	@param newRecord new
	 *	@return save
	 */
	@Override
	protected boolean beforeSave (boolean newRecord)
	{
		if (isComplete() && 
			! is_ValueChanged(COLUMNNAME_Processed) &&
            (   is_ValueChanged(COLUMNNAME_C_BankAccount_ID)
             || is_ValueChanged(COLUMNNAME_C_BPartner_ID)
             || is_ValueChanged(COLUMNNAME_C_Charge_ID)
             || is_ValueChanged(COLUMNNAME_C_Currency_ID)
             || is_ValueChanged(COLUMNNAME_C_DocType_ID)
             || is_ValueChanged(COLUMNNAME_DateAcct)
             || is_ValueChanged(COLUMNNAME_DateTrx)
             || is_ValueChanged(COLUMNNAME_DiscountAmt)
             || is_ValueChanged(COLUMNNAME_PayAmt)
             || is_ValueChanged(COLUMNNAME_WriteOffAmt))) {
			log.saveError("PaymentAlreadyProcessed", Msg.translate(getCtx(), "C_Payment_ID"));
			return false;
		}
		// @Trifon - CashPayments
		//if ( getTenderType().equals("X") ) {
		if ( isCashbookTrx()) {
			// Cash Book Is mandatory
			if ( getC_CashBook_ID() <= 0 ) {
				log.saveError("Error", Msg.parseTranslation(getCtx(), "@Mandatory@: @C_CashBook_ID@"));
				return false;
			}
		} else {
			// Bank Account Is mandatory
			if ( getC_BankAccount_ID() <= 0 ) {
				log.saveError("Error", Msg.parseTranslation(getCtx(), "@Mandatory@: @C_BankAccount_ID@"));
				return false;
			}
		}
		// end @Trifon - CashPayments
		
		//	We have a charge
		if (getC_Charge_ID() != 0) 
		{
			if (newRecord || is_ValueChanged("C_Charge_ID"))
			{
				setC_Order_ID(0);
				setC_Invoice_ID(0);
				setWriteOffAmt(Env.ZERO);
				setDiscountAmt(Env.ZERO);
				setIsOverUnderPayment(false);
				setOverUnderAmt(Env.ZERO);
				setIsPrepayment(false);
			}
		}
		//	We need a BPartner
		else if (getC_BPartner_ID() == 0 && !isCashTrx())
		{
			if (getC_Invoice_ID() != 0)
				;
			else if (getC_Order_ID() != 0)
				;
			else
			{
				log.saveError("Error", Msg.parseTranslation(getCtx(), "@NotFound@: @C_BPartner_ID@"));
				return false;
			}
		}
		//	Prepayment: No charge and order or project (not as acct dimension)
		/*if (newRecord 
			|| is_ValueChanged("C_Charge_ID") || is_ValueChanged("C_Invoice_ID")
			|| is_ValueChanged("C_Order_ID") || is_ValueChanged("C_Project_ID"))
		{
			if (getReversal_ID() > 0)
			{
				setIsPrepayment(getReversal().isPrepayment());
			}
			else
			{
				setIsPrepayment (getC_Charge_ID() == 0 
					&& getC_BPartner_ID() != 0
					&& (getC_Order_ID() != 0 
						|| (getC_Project_ID() != 0 && getC_Invoice_ID() == 0)));
			}
		}*/
		
		if (isPrepayment())
		{
			if (newRecord 
				|| is_ValueChanged("C_Order_ID") || is_ValueChanged("C_Project_ID"))
			{
				setWriteOffAmt(Env.ZERO);
				setDiscountAmt(Env.ZERO);
				setIsOverUnderPayment(false);
				setOverUnderAmt(Env.ZERO);
			}
		}
		
		//	Document Type/Receipt
		if (getC_DocType_ID() == 0)
			setC_DocType_ID();
		else
		{
			MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());
			setIsReceipt(dt.isSOTrx());
		}
		setDocumentNo();
		//
		if (getDateAcct() == null)
			setDateAcct(getDateTrx());
		//
		if (!isOverUnderPayment())
			setOverUnderAmt(Env.ZERO);
		
		//	Organization
		if ((newRecord || is_ValueChanged("C_BankAccount_ID"))
			&& getC_Charge_ID() == 0)	//	allow different org for charge
		{
			MBankAccount ba = MBankAccount.get(getCtx(), getC_BankAccount_ID());
			if (ba.getAD_Org_ID() != 0)
				setAD_Org_ID(ba.getAD_Org_ID());
		}
		
		// [ adempiere-Bugs-1885417 ] Validate BP on Payment Prepare or BeforeSave
		// there is bp and (invoice or order)
		if (getC_BPartner_ID() != 0 && (getC_Invoice_ID() != 0 || getC_Order_ID() != 0)) {
			if (getC_Invoice_ID() != 0) {
				MInvoice inv = new MInvoice(getCtx(), getC_Invoice_ID(), get_TrxName());
				if (inv.getC_BPartner_ID() != getC_BPartner_ID()) {
					log.saveError("Error", Msg.parseTranslation(getCtx(), "BP different from BP Invoice"));
					return false;
				}
			}
			if (getC_Order_ID() != 0) {
				MOrder ord = new MOrder(getCtx(), getC_Order_ID(), get_TrxName());
				if (ord.getC_BPartner_ID() != getC_BPartner_ID()) {
					log.saveError("Error", Msg.parseTranslation(getCtx(), "BP different from BP Order"));
					return false;
				}
			}
		}
		
		if (isProcessed())
		{
			if (getCreditCardNumber() != null)
			{
				String encrpytedCCNo = PaymentUtil.encrpytCreditCard(getCreditCardNumber());
				if (!encrpytedCCNo.equals(getCreditCardNumber()))
					setCreditCardNumber(encrpytedCCNo);
			}
			
			if (getCreditCardVV() != null)
			{
				String encrpytedCvv = PaymentUtil.encrpytCvv(getCreditCardVV());
				if (!encrpytedCvv.equals(getCreditCardVV()))
					setCreditCardVV(encrpytedCvv);
			}
		}

		if (MSysConfig.getBooleanValue(MSysConfig.IBAN_VALIDATION, true, Env.getAD_Client_ID(Env.getCtx()))) {
			if (!Util.isEmpty(getIBAN())) {
				setIBAN(IBAN.normalizeIBAN(getIBAN()));
				if (!IBAN.isValid(getIBAN())) {
					log.saveError("Error", Msg.getMsg(getCtx(), "InvalidIBAN"));
					return false;
				}
			}
		}
		
		return true;
	}	//	beforeSave
	
}
