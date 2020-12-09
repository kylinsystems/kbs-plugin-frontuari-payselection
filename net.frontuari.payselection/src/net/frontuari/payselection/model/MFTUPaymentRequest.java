package net.frontuari.payselection.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MCurrency;
import org.compiere.model.MDocType;
import org.compiere.model.MPeriod;
import org.compiere.model.MPeriodControl;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.process.DocOptions;
import org.compiere.process.DocumentEngine;
import org.compiere.util.CCache;
import org.compiere.util.DB;
import org.compiere.util.Env;

/**
 * Payment Request Model
 * @author <a href="mailto:jcolmenarez@frontuari.net">Jorge Colmenarez</a>
 *
 */
public class MFTUPaymentRequest extends X_FTU_PaymentRequest implements DocAction, DocOptions {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5771238236814875408L;


	/** Invoice = ARI */
	public static final String REQUESTTYPE_ARInvoice = "ARI";
	
	public MFTUPaymentRequest(Properties ctx, int FTU_PaymentRequest_ID, String trxName) {
		super(ctx, FTU_PaymentRequest_ID, trxName);
		if (FTU_PaymentRequest_ID == 0) {
			setDocAction (DOCACTION_Complete);	// CO
			setDocStatus (DOCSTATUS_Drafted);	// DR
			setIsApproved (false);
			setProcessed (false);
		}
	}

	public MFTUPaymentRequest(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}
	
	
	/**	Cache						*/
	private static CCache<Integer,MFTUPaymentRequest>	s_cache	= new CCache<Integer,MFTUPaymentRequest>("MFTUPaymentRequest", 20, 2);	//	2 minutes
	/**
	 * 
	 * @author <a href="mailto:jcolmenarez@frontuari.net">Jorge Colmenarez</a> 2020-10-03, 12:20
	 * @param ctx
	 * @param p_FTU_PaymentRequest_ID
	 * @return MFTUPaymentRequest
	 */
	public static MFTUPaymentRequest get(Properties ctx, int p_FTU_PaymentRequest_ID) {
		Integer key = new Integer (p_FTU_PaymentRequest_ID);
		MFTUPaymentRequest retValue = (MFTUPaymentRequest) s_cache.get (key);
		if (retValue != null)
			return retValue;
		retValue = new MFTUPaymentRequest (ctx, p_FTU_PaymentRequest_ID, null);
		if (retValue.get_ID () != 0)
			s_cache.put (key, retValue);
		return retValue;
	}

	@Override
	public int customizeValidActions(String docStatus, Object processing, String orderType, String isSOTrx,
			int AD_Table_ID, String[] docAction, String[] options, int index) {
		//	Valid Document Action
		if (AD_Table_ID == Table_ID){
			if (docStatus.equals(DocumentEngine.STATUS_Drafted)
					/*|| docStatus.equals(DocumentEngine.STATUS_InProgress)*/
					|| docStatus.equals(DocumentEngine.STATUS_Invalid))
				{
					options[index++] = DocumentEngine.ACTION_Prepare;
				}else if (docStatus.equals(DocumentEngine.STATUS_InProgress)) {
					options[index++] = DocumentEngine.ACTION_Approve;
				}
				else if (docStatus.equals(DocumentEngine.STATUS_Approved)) {
					options[index++] = DocumentEngine.ACTION_Complete;
				}
				//	Complete                    ..  CO
				else if (docStatus.equals(DocumentEngine.STATUS_Completed))
				{
					options[index++] = DocumentEngine.ACTION_Void;
					options[index++] = DocumentEngine.ACTION_Close;
					options[index++] = DocumentEngine.ACTION_ReActivate;
				}
		}
		
		return index;
	}

	/**	Process Message 			*/
	private String		m_processMsg = null;
	
	/**	Just Prepared Flag			*/
	private boolean		m_justPrepared = false;
	
	/**	Lines						*/
	private MFTUPaymentRequestLine[]	m_lines = null;

	@Override
	public boolean processIt(String processAction) throws Exception {
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}

	@Override
	public boolean unlockIt() {
		log.info(toString());
		return true;
	}

	@Override
	public boolean invalidateIt() {
		log.info(toString());
		setDocAction(DOCACTION_Prepare);
		return true;
	}

	@Override
	public String prepareIt() {
		log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		//	Std Period open?
		MPeriod.testPeriodOpen(getCtx(), getDateDoc(), MDocType.DOCBASETYPE_PaymentAllocation, getAD_Org_ID());
		getLines(false);
		if (m_lines.length == 0) {
			m_processMsg = "@NoLines@";
			return DocAction.STATUS_Invalid;
		}
		
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		m_justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		
		return DocAction.STATUS_InProgress;
	}

	@Override
	public boolean approveIt() {
		log.info(toString());
		setIsApproved(true);
		return true;
	}

	@Override
	public boolean rejectIt() {
		log.info(toString());
		setIsApproved(false);
		return true;
	}

	@Override
	public String completeIt() {
		//	Re-Check
		if (!m_justPrepared) {
			String status = prepareIt();
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		//	Implicit Approval
		if (!isApproved())
			approveIt();
		log.info(toString());
		
		//	Valid Amount
		if(getPayAmt() == null
				|| getPayAmt().doubleValue() <= 0) {
			m_processMsg = "@PayAmt@ <= @0@";
			return DocAction.STATUS_Invalid;
		}
		
		m_processMsg = validateDocumentLines();
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		//	User Validation
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null){
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}

		setProcessed(true);
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}
	
	@Override
	public boolean voidIt() {
		log.info(toString());
		// Before Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;
		//	Reverse Document
		boolean retValue = reverseIt();
		//	Validate request line with payment completed
		m_processMsg = validReferencePayment();
		if(m_processMsg != null)
			return false;
		// After Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_VOID);
		if (m_processMsg != null)
			return false;
		//	Set Doc Action
		setDocAction(DOCACTION_None);
		//
		return retValue;
	}

	/**
	 * 
	 * @author <a href="mailto:dixon.22martinez@gmail.com">Dixon Martinez</a> 11/07/2014, 15:26:42
	 * @return
	 * @return String
	 */
	private String validReferencePayment() {
		String sql ="SELECT 	COALESCE(p.DocumentNo,ps.Name)  DocumentNoReferenced, prl.Line, psc.PayAmt, "
				+ "COALESCE(o.DocumentNO, i.DocumentNO, j.DocumentNO) DocumentNo, pr.RequestType, bp.Name "
				+ "FROM FTU_PaymentRequestLine prl "
				+ "INNER JOIN FTU_PaymentRequest pr ON (pr.FTU_PaymentRequest_ID = prl.FTU_PaymentRequest_ID) "
				+ "INNER JOIN C_PaySelectionLine psl  ON (prl.FTU_PaymentRequestLine_ID = psl.FTU_PaymentRequestLine_ID AND psl.IsActive = 'Y') "
				+ "INNER JOIN C_PaySelectionCheck psc ON (psc.C_PaySelectionCheck_ID = psl.C_PaySelectionCheck_ID) "
				+ "INNER JOIN C_PaySelection ps ON (psl.C_PaySelection_ID = ps.C_PaySelection_ID AND ps.IsActive = 'Y') "
				+ "LEFT JOIN C_Payment p ON (psc.C_Payment_ID = p.C_Payment_ID ) "
				+ "LEFT JOIN C_Order o ON (o.C_Order_ID = prl.C_Order_ID) "
				+ "LEFT JOIN GL_JournalLine jl ON (jl.GL_JournalLine_ID = prl.GL_JournalLine_ID) "
				+ "LEFT JOIN GL_Journal j ON (jl.GL_Journal_ID = j.GL_Journal_ID) "
				+ "LEFT JOIN C_Invoice i ON (i.C_Invoice_ID = prl.C_Invoice_ID ) "
				+ "LEFT JOIN C_BPartner bp ON (bp.C_BPartner_ID = prl.C_BPartner_ID) "
				+ "WHERE	(p.DocStatus IN ('CO','CL') OR p.C_Payment_ID IS NULL) AND prl.FTU_PaymentRequest_ID = ?";
		//	Precision
		int precision = MCurrency.getStdPrecision(getCtx(), Env.getContextAsInt(getCtx(), "$C_Currency_ID"));
		StringBuffer msgLong = new StringBuffer();
		PreparedStatement ps = null;
		ResultSet rs = null;
		int line = 0;
		String documentNoReferenced = null;
		String documentNo = null;
		String p_RequestType = null;
		String p_BPName = null;
		String msg = null;
		//BigDecimal openAmt = null;
		BigDecimal payAmt = null;
		try {
			ps = DB.prepareStatement(sql,get_TrxName());
			ps.setInt(1, getFTU_PaymentRequest_ID());
			rs = ps.executeQuery();
			//	Iterate
			while (rs.next()) {
				line = rs.getInt("Line");
				documentNoReferenced = rs.getString("DocumentNoReferenced");
				payAmt = rs.getBigDecimal("PayAmt");
				documentNo = rs.getString("DocumentNo");
				p_RequestType = rs.getString("RequestType");
				p_BPName = rs.getString("Name");
				//	Valid Payment Amount
				if(payAmt == null)
					payAmt = Env.ZERO;
				//	Set Precision
				if(payAmt != null
						&& payAmt.precision() > precision)
					payAmt = payAmt.setScale(precision, BigDecimal.ROUND_HALF_UP);
				msg = " @Line@ " + line ;
				if (p_RequestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_PurchaseOrder))
					msg += " @C_Order_ID@ ";
				else if (p_RequestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_APInvoice))
					msg += " @C_Invoice_ID@ ";
				else if (p_RequestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_GLJournal))
					msg += " @GL_Journal_ID@ ";
				else if (p_RequestType.equals(X_FTU_PaymentRequest.REQUESTTYPE_PaymentRequestManual))
					msg += " @C_BPartner_ID@ ";
				
				//	Evaluate Error
				
				msg += documentNo == null ? "" : documentNo ; 
				msg += p_BPName == null ? "" : p_BPName ;  
				msg += " @SQLErrorReferenced@ " +  
						" @C_Payment_ID@/@C_PaySelection_ID@ " + documentNoReferenced
						;
				//	
				if(msgLong.length() != 0)
					msgLong
						.append("\n")
						.append("*")
						.append(msg)
						.append("*");
				else
					msgLong
						.append("*")
						.append(msg)
						.append("*");
			}
		} catch (SQLException e) {
			throw new AdempiereException(e.getMessage());
		}finally{
    		DB.close(rs, ps);
    		rs = null; ps= null;
    	}
		//
		if(msgLong.toString().length() > 0 )
			return msgLong.toString();
		else 
			return null;
	}
	
	@Override
	public boolean closeIt() {
		log.info(toString());
		// Before Close
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_CLOSE);
		if (m_processMsg != null)
			return false;

		setDocAction(DOCACTION_None);

		// After Close
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_CLOSE);
		if (m_processMsg != null)
			return false;

		return true;
	}

	@Override
	public boolean reverseCorrectIt() {
		log.info(toString());
		// Before reverseCorrect
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REVERSECORRECT);
		if (m_processMsg != null)
			return false;
		
		boolean retValue = reverseIt();

		// After reverseCorrect
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REVERSECORRECT);
		if (m_processMsg != null)
			return false;
		
		setDocAction(DOCACTION_None);
		return retValue;
	}

	@Override
	public boolean reverseAccrualIt() {
		log.info(toString());
		// Before reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;
		
		boolean retValue = reverseIt();

		// After reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;
		
		setDocAction(DOCACTION_None);
		return retValue;
	}

	@Override
	public boolean reActivateIt() {
		log.info(toString());
		// Before reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REACTIVATE);
		if (m_processMsg != null)
			return false;
		
		setProcessed(false);
		setIsApproved(false);
		setDocAction(DOCACTION_Complete);
		// After reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REACTIVATE);
		if (m_processMsg != null)
			return false;
		
		return true;
	}

	@Override
	public String getSummary() {
		StringBuffer sb = new StringBuffer();
		sb.append(getDocumentNo());
		sb.append(": ")
			.append(" (#").append(getLines(false).length).append(")");
		if (getDescription() != null && getDescription().length() > 0)
			sb.append(" - ").append(getDescription());
		return sb.toString();
	}

	@Override
	public String getDocumentInfo() {
		MDocType docType = MDocType.get(getCtx(), getC_DocType_ID());
		StringBuilder documentInfo = new StringBuilder();
		documentInfo.append(docType.getNameTrl());
		documentInfo.append(" ");
		documentInfo.append(getDocumentNo());
		// CUSTOM DOCUMENT INFO
		return documentInfo.toString();
	}

	@Override
	public File createPDF() {
		try
		{
			File temp = File.createTempFile(get_TableName()+get_ID()+"_", ".pdf");
			return createPDF (temp);
		}
		catch (Exception e)
		{
			log.severe("Could not create PDF - " + e.getMessage());
		}
		return null;
	}

	@Override
	public String getProcessMsg() {
		return m_processMsg;
	}

	@Override
	public int getDoc_User_ID() {
		return getCreatedBy();
	}

	@Override
	public BigDecimal getApprovalAmt() {
		return null;
	}
	
	/**
	 * 	Create PDF file
	 *	@param file output file
	 *	@return file if success
	 */
	public File createPDF (File file)
	{
	//	ReportEngine re = ReportEngine.get (getCtx(), ReportEngine.INVOICE, getC_Invoice_ID());
	//	if (re == null)
			return null;
	//	return re.getPDF(file);
	}	//	createPDF

	/**************************************************************************
	 * 	Reverse Allocation.
	 * 	Period needs to be open
	 *	@return true if reversed
	 */
	private boolean reverseIt() 
	{
		if (!isActive())
			throw new IllegalStateException("Document already reversed (not active)");

		//	Can we delete posting
		MPeriod.testPeriodOpen(getCtx(), getDateDoc(), MPeriodControl.DOCBASETYPE_PaymentAllocation, getAD_Org_ID());

		//	Set Inactive
		setIsActive (false);
		setDocumentNo(getDocumentNo()+"^");
		setDocStatus(DOCSTATUS_Reversed);	//	for direct calls
		if (!save() || isActive())
			throw new IllegalStateException("Cannot de-activate payment request");
			
		//	Unlink request line
		getLines(true);
		for (int i = 0; i < m_lines.length; i++) {
			MFTUPaymentRequestLine line = m_lines[i];
			line.setIsActive(false);
			line.save();
		}
		return true;
	}	//	reverse

	/**
	 * 	Get Lines
	 *	@param requery if true requery
	 *	@return lines
	 */
	public MFTUPaymentRequestLine[] getLines (boolean requery) {
		if (m_lines != null 
				&& m_lines.length 
					!= 0 && !requery) {
			set_TrxName(m_lines, get_TrxName());
			return m_lines;
		}
		//	Get from DB
		List<MFTUPaymentRequestLine> list = new Query(getCtx(), I_FTU_PaymentRequestLine.Table_Name, 
						COLUMNNAME_FTU_PaymentRequest_ID + "=?", get_TrxName())
				.setParameters(getFTU_PaymentRequest_ID())
				.<MFTUPaymentRequestLine>list();
		//	Convert to array
		m_lines = new MFTUPaymentRequestLine[list.size()];
		list.toArray (m_lines);
		//	Return
		return m_lines;
	}	//	getLines

	/**
	 * 	Get Lines Where Clause
	 *	@param requery if true requery
	 *	@return lines
	 */
	public MFTUPaymentRequestLine[] getLines (boolean requery, String whereClause) {
		if (m_lines != null 
				&& m_lines.length 
					!= 0 && !requery) {
			set_TrxName(m_lines, get_TrxName());
			return m_lines;
		}

		whereClause = 
				whereClause != null 
					? COLUMNNAME_FTU_PaymentRequest_ID + "=? AND " + whereClause 
							: COLUMNNAME_FTU_PaymentRequest_ID + "=?"; 
		
		//	Get from DB
		List<MFTUPaymentRequestLine> list = new Query(getCtx(), I_FTU_PaymentRequestLine.Table_Name, 
				whereClause, get_TrxName())
				.setParameters(getFTU_PaymentRequest_ID())
				.<MFTUPaymentRequestLine>list();
		//	Convert to array
		m_lines = new MFTUPaymentRequestLine[list.size()];
		list.toArray (m_lines);
		//	Return
		return m_lines;
	}	//	getLines
	
	/**
	 * Override setProcessed to Process Payment Request Lines 
	 * @author <a href="mailto:jcolmenarez@frontuari.net">Jorge Colmenarez</a> 2020-10-03, 12:32
	 * @return String
	 */
	@Override
	public void setProcessed(boolean Processed) {
		super.setProcessed(Processed);
		if (get_ID() == 0)
			return;
		String sql = "UPDATE FTU_PaymentRequestLine SET Processed='"
			+ (Processed ? "Y" : "N")
			+ "' WHERE FTU_PaymentRequest_ID=" + getFTU_PaymentRequest_ID() + " AND IsPrepared = 'N' ";
		int noLine = DB.executeUpdate(sql, get_TrxName());
		m_lines = null;
		log.fine(Processed + " - Lines=" + noLine);
	}
	
	/**
	 * 
	 * @author <a href="mailto:jcolmenarez@frontuari.net">Jorge Colmenarez</a> 2020-10-03, 12:32
	 * @return String
	 */
	private String validateDocumentLines(){
		StringBuffer sql = new StringBuffer();
		PreparedStatement ps = null;
		int numRecords = 0;
		String msg = null;
		ResultSet rs = null;
		boolean prManual = getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_PaymentRequestManual);
		int scale = MCurrency.getStdPrecision(getCtx(), Env.getContextAsInt(getCtx(), "$C_Currency_ID"));
		
		if (getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_APInvoice))
			sql.append("SELECT  " + 
						"COALESCE(prl.Line,0) Line, " + // 1 
						"COALESCE(i.DocumentNo,'') DocumentNo, " + // 2
						"COALESCE(currencyconvert(PaymentRequestOpen(pr.RequestType,prl.C_Invoice_ID,null),i.C_Currency_ID,pr.C_Currency_ID"
						//Add Conversion By Type of Negotiation By Argenis Rodríguez 09-12-2020
							+ ", CASE WHEN COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN pr.DateDoc"
							+ " ELSE i.DateInvoiced END"
						//End By Argenis Rodríguez
						+ ",i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID),0) OpenAmt, " + //3
						"COALESCE(prl.PayAmt,0) PayAmt , " + //4
						//"COALESCE(prl.IsExceededAmt,'N') IsExceededAmt, " + //5
						"COALESCE(i.DocStatus,'') DocStatus, " + //6
						"COALESCE(prlg.QtyRecords,1) QtyRecords, " + //7
						"COALESCE(prl.IsPrepared,'N') IsPrepared " + //8
						"FROM  " +
						"FTU_PaymentRequestLine prl " +
						"INNER JOIN FTU_PaymentRequest pr ON (pr.FTU_PaymentRequest_ID=prl.FTU_PaymentRequest_ID) " +
						"INNER JOIN C_Invoice i ON (i.C_Invoice_ID=prl.C_Invoice_ID) " +
						"INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = i.C_BPartner_ID) " +
						"INNER JOIN (SELECT prl.C_Invoice_ID,prl.FTU_PaymentRequest_ID,COUNT(prl.C_Invoice_ID) QtyRecords " +
						"FROM FTU_PaymentRequestLine prl " + 
						"GROUP BY prl.C_Invoice_ID,prl.FTU_PaymentRequest_ID) " + 
						"prlg ON (prl.C_Invoice_ID =prlg.C_Invoice_ID AND prl.FTU_PaymentRequest_ID = prlg.FTU_PaymentRequest_ID) " +
						"WHERE pr.FTU_PaymentRequest_ID=? ");
		
		if (getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_PurchaseOrder))
			sql.append("SELECT  " + 
						"COALESCE(prl.Line,0) Line, " + // 1 
						"COALESCE(o.DocumentNo,'') DocumentNo, " + // 2
						"COALESCE(currencyconvert(PaymentRequestOpen(pr.RequestType,prl.C_Order_ID,null),o.C_Currency_ID,pr.C_Currency_ID"
						//Add Conversion By Negotiation Type By Argenis Rodríguez
							+ ", CASE WHEN COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN pr.DateDoc"
							+ " ELSE o.DateOrdered END"
						//End By Argenis Rodríguez
						+ ",o.C_ConversionType_ID,o.AD_Client_ID,o.AD_Org_ID),0) OpenAmt, " + //3
						"COALESCE(prl.PayAmt,0) PayAmt , " + //4
						//"COALESCE(prl.IsExceededAmt,'N') IsExceededAmt, " + //5
						"COALESCE(o.DocStatus,'') DocStatus, " + //6
						"COALESCE(prlg.QtyRecords,1) QtyRecords, " + //7
						"COALESCE(prl.IsPrepared,'N') IsPrepared " + //8
						"FROM  " +
						"FTU_PaymentRequestLine prl " +
						"INNER JOIN FTU_PaymentRequest pr ON (pr.FTU_PaymentRequest_ID=prl.FTU_PaymentRequest_ID) " +
						"INNER JOIN C_Order o ON (o.C_Order_ID=prl.C_Order_ID) " +
						"INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = o.C_BPartner_ID) " +
						"INNER JOIN (SELECT prl.C_Order_ID,prl.FTU_PaymentRequest_ID,COUNT(prl.C_Order_ID) QtyRecords " +
						"FROM FTU_PaymentRequestLine prl " + 
						"GROUP BY prl.C_Order_ID,prl.FTU_PaymentRequest_ID) " + 
						"prlg ON (prl.C_Order_ID =prlg.C_Order_ID AND prl.FTU_PaymentRequest_ID = prlg.FTU_PaymentRequest_ID) " +
						"WHERE pr.FTU_PaymentRequest_ID=? ");
		
		
		if (getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_GLJournal))
			sql.append("SELECT " + 
						"COALESCE(prl.Line,0) Line, " + // 1 
						"COALESCE(op.DocumentNo,'') DocumentNo, " + // 2
						"COALESCE(currencyconvert(op.OpenAmt,op.C_Currency_ID,pr.C_Currency_ID"
						//Add Conversion By Type of Negotiation By Argenis Rodríguez
							+ ", CASE WHEN COALESCE(bp.TypeNegotiation, 'DP') = 'DP' THEN pr.DateDoc"
							+ " ELSE prlg.DateDoc END"
						//End By Argenis Rodríguez
						+ ",op.C_ConversionType_ID,pr.AD_Client_ID,pr.AD_Org_ID),0) OpenAmt, " + //3
						"COALESCE(prl.PayAmt,0) PayAmt , " + //4
						//"COALESCE(prl.IsExceededAmt,'N') IsExceededAmt, " + //5
						"COALESCE(op.DocStatus,'') DocStatus, " + //6
						"COALESCE(prlg.QtyRecords,1) QtyRecords, " + //7
						"COALESCE(prl.IsPrepared,'N') IsPrepared " + //8
						"FROM  " +
						"FTU_PaymentRequestLine prl " +
						"INNER JOIN FTU_PaymentRequest pr ON (pr.FTU_PaymentRequest_ID=prl.FTU_PaymentRequest_ID) " +
						"INNER JOIN FTU_RV_OpenPayment op ON (op.Record_ID=prl.GL_Journal_ID AND op.RequestType = 'GLJ') " +
						"INNER JOIN (SELECT prl.GL_Journal_ID,prl.FTU_PaymentRequest_ID,COUNT(prl.GL_Journal_ID) QtyRecords " +
						", bp_1.C_BPartner_ID, glj.DateDoc " +
						"FROM FTU_PaymentRequestLine prl " +
						"INNER JOIN GL_Journal glj ON (glj.GL_Journal_ID = prl.GL_Journal_ID) " +
						"INNER JOIN C_BPartner bp_1 ON (bp_1.C_BPartner_ID = glj.C_BPartner_ID) " +
						"GROUP BY prl.GL_Journal_ID,prl.FTU_PaymentRequest_ID, bp_1.C_BPartner_ID, glj.DateDoc) " + 
						"prlg ON (prl.GL_Journal_ID =prlg.GL_Journal_ID AND prl.FTU_PaymentRequest_ID = prlg.FTU_PaymentRequest_ID) " +
						"INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = prlg.C_BPartner_ID) " +
						"WHERE pr.FTU_PaymentRequest_ID=? ");
		if (getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_PaymentRequestManual)) //Only Check Lines 
			sql.append("SELECT 1 " + 
						"FROM  " +
						"FTU_PaymentRequestLine prl " +
						"WHERE prl.FTU_PaymentRequest_ID=? ");
		if (getRequestType().equals(REQUESTTYPE_ARInvoice))
			sql.append("SELECT  " + 
					"COALESCE(prl.Line,0) Line, " + // 1 
					"COALESCE(i.DocumentNo,'') DocumentNo, " + // 2
					//"COALESCE(currencyconvert(PaymentRequestOpen(pr.RequestType,prl.C_Invoice_ID,null),i.C_Currency_ID,pr.C_Currency_ID,pr.DateDoc,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID),0) OpenAmt, " + //3
					"COALESCE(prl.PayAmt,0)"
						+ "-COALESCE((SELECT SUM(currencyconvert(prl_1.PayAmt,pr_1.C_Currency_ID,pr.C_Currency_ID"
						//Add Conversion By Type of Negotiation By Argenis Rodríguez
								+ ", CASE WHEN COALESCE(cb_1.TypeNegotiation, 'DP') = 'DP' THEN pr_1.DateDoc"
								+ " ELSE i_1.DateInvoiced END"
						//End By Argenis Rodríguez
							+ ",i_1.C_ConversionType_ID,i_1.AD_Client_ID,i_1.AD_Org_ID)) FROM FTU_PaymentRequestLine prl_1 JOIN FTU_PaymentRequest pr_1 ON pr_1.FTU_PaymentRequest_ID=prl_1.FTU_PaymentRequest_ID"
							+ " JOIN C_Invoice i_1 ON prl_1.c_invoice_id=i_1.c_invoice_id INNER JOIN C_BPartner cb_1 ON cb_1.C_BPartner_ID = i_1.C_BPartner_ID WHERE pr_1.DocStatus IN ('CO','CL') AND prl_1.c_invoice_id=prl.c_invoice_id),0) OpenAmt , " + //3 delete this validation 
					"COALESCE(prl.PayAmt,0) PayAmt , " + //4
					//"COALESCE(prl.IsExceededAmt,'N') IsExceededAmt, " + //5
					"COALESCE(i.DocStatus,'') DocStatus, " + //6
					"COALESCE(prlg.QtyRecords,1) QtyRecords, " + //7
					"COALESCE(prl.IsPrepared,'N') IsPrepared " + //8
					"FROM  " +
					"FTU_PaymentRequestLine prl " +
					"INNER JOIN FTU_PaymentRequest pr ON (pr.FTU_PaymentRequest_ID=prl.FTU_PaymentRequest_ID) " +
					"INNER JOIN C_Invoice i ON (i.C_Invoice_ID=prl.C_Invoice_ID) " +
					"INNER JOIN C_BPartner bp ON (bp.C_BPartner_ID = i.C_BPartner_ID) " +
					"INNER JOIN (SELECT prl.C_Invoice_ID,prl.FTU_PaymentRequest_ID,COUNT(prl.C_Invoice_ID) QtyRecords " +
					"FROM FTU_PaymentRequestLine prl " + 
					"GROUP BY prl.C_Invoice_ID,prl.FTU_PaymentRequest_ID) " + 
					"prlg ON (prl.C_Invoice_ID =prlg.C_Invoice_ID AND prl.FTU_PaymentRequest_ID = prlg.FTU_PaymentRequest_ID) " +
					"WHERE pr.FTU_PaymentRequest_ID=? ");
		
		try{
			ps = DB.prepareStatement(sql.toString(), get_TrxName());
			ps.setInt(1, getFTU_PaymentRequest_ID());
			rs = ps.executeQuery();
			while (rs.next()){
				numRecords++;
				if (prManual)
					break;
				
				//Duplicate Documents in Payment Request
				if (rs.getInt(6) > 1)
					msg = (msg==null ? "" : msg) + 
							"\n"  + 
							"*"  + 
							" @Duplicate@ @Line@ " + rs.getInt(1) + " @DocumentNo@ " + rs.getString(2) + " " +
							"*";
				
				//Valid Payment Amount
				if (rs.getBigDecimal(4).setScale(scale).compareTo(rs.getBigDecimal(3).setScale(scale)) == 1
						//Not Exceeded Amt Lines 
						//&& rs.getString(5).equals("N")
							//Not Prepared Lines
							&& rs.getString(7).equals("N")
						)
					msg = (msg==null ? "" : msg) + 
							"\n"  + 
							"*"  + 
							" @Line@ " + rs.getInt(1) + " @DocumentNo@ " + rs.getString(2) + " @PayAmt@= " + rs.getBigDecimal(4).setScale(scale).doubleValue() + 
							" > " + " @OpenAmt@= " + rs.getBigDecimal(3).setScale(scale).doubleValue() + 
							" @Difference@=" + rs.getBigDecimal(4).setScale(scale).subtract(rs.getBigDecimal(3).setScale(scale)) +  
							"*";
				
				//Valid Document Status
				if (rs.getString(7).equals("N")){
					if ((getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_PurchaseOrder)
							&&
							rs.getString(5).equals(X_FTU_PaymentRequest.DOCSTATUS_Completed))
						||
						(!getRequestType().equals(X_FTU_PaymentRequest.REQUESTTYPE_PurchaseOrder)
								&&
								(rs.getString(5).equals(X_FTU_PaymentRequest.DOCSTATUS_Completed)
										|| rs.getString(5).equals(X_FTU_PaymentRequest.DOCSTATUS_Closed))
						)
					)
						;
					else
						msg = (msg==null ? "" : msg) + 
								"\n"  + 
								"*"  + 
								" @Invalid@ @DocStatus@ @Line@ " + rs.getInt(1) + " @DocumentNo@ " + rs.getString(2) +  
								"*";
				}
			}
		}catch(Exception e){
			throw new AdempiereException(e);
		}
		finally{
			DB.close(rs, ps);
			rs = null; ps = null;
		}
		if (numRecords==0)
			msg = "@NoLines@";
		return msg;
	}

}
