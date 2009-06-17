package org.sakaiproject.contentreview.impl.turnitin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * This is a utility class for wrapping the physical https calls to the
 * Turn It In Service.
 * 
 * @author sgithens
 *
 */
public class TurnitinAPIUtil {
	private static final Log log = LogFactory.getLog(TurnitinAPIUtil.class);
	
	
	private void enrollInClass(String cid, String ctl, String userId, 
			String tem, String uem,
			String ufn, String uln, String uid, String sendNotifications,
			String secretKey, String aid, String said, String apiURL,
			Proxy proxy) throws SubmissionException {

		String fid = "3";
		String fcmd = "2";
		String encrypt = "0";
		String diagnostic = "0";

		String utp = "1";

		String gmtime = getGMTime();

		String md5_str = aid + cid + ctl + diagnostic + sendNotifications +  encrypt + fcmd + fid + gmtime + said + tem + uem +
		ufn + uid + uln + utp + secretKey;

		String md5;
		try{
			md5 = getMD5(md5_str);
		} catch (Exception t) {
			log.warn("MD5 error enrolling student on turnitin");
			throw new SubmissionException("Cannot generate MD5 hash for Class Enrollment Turnitin API call", t);
		}

		HttpsURLConnection connection;

		try {
			connection = fetchConnection(apiURL, proxy);

			log.debug("Connection made to Turnitin");

			OutputStream outStream = connection.getOutputStream();

			writeBytesToOutputStream(outStream, 
				"fid=", fid, 
				"&fcmd=", fcmd,
				"&cid=", cid,
				"&tem=", tem,
				"&ctl=", ctl,
				"&encrypt=", encrypt,
				"&aid=", aid,
				"&said=", said,
				"&diagnostic=", diagnostic,
				"&dis=", Integer.valueOf(sendNotifications).toString(),
				"&uem=", URLEncoder.encode(uem, "UTF-8"),
				"&ufn=", ufn,
				"&uln=", uln,
				"&utp=", utp,
				"&gmtime=", URLEncoder.encode(gmtime, "UTF-8"),
				"&md5=", md5,
				"&uid=", uid
			);

			outStream.close();
		} catch (MalformedURLException e) {
			throw new SubmissionException("Student Enrollment call to Turnitin failed", e);
		} catch (IOException e) {
			throw new SubmissionException("Student Enrollment call to Turnitin failed", e);		
		}

		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		} catch (Exception t) {
			throw new SubmissionException ("Cannot get Turnitin response. Assuming call was unsuccessful", t);
		}

		
		try {	
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder  parser = documentBuilderFactory.newDocumentBuilder();
			parser.parse(new org.xml.sax.InputSource(in));
		}
		catch (ParserConfigurationException pce){
			log.error("parser configuration error: " + pce.getMessage());
		} catch (Exception t) {
			throw new SubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful", t);
		}
	}
	 
	
	public static void createClass(String cid, String ctl, String cpw,
			String uem, String ufn, String uln, String upw, String uid, String aid,
			String secretKey, String said, String apiURL, Proxy proxy ) throws SubmissionException, TransientSubmissionException {

		String diagnostic = "0";
		String encrypt = "0";	
		String fcmd = "2";
		String fid = "2";
		String utp = "2"; 					//user type 2 = instructor

		String gmtime = getGMTime();

		// MD5 of function 2 - Create a class under a given account (instructor only)
		String md5_str = aid + cid + cpw + ctl + diagnostic + encrypt + fcmd + fid +
		gmtime + said + uem + ufn + uid + uln + upw + utp + secretKey;

		String md5;
		try{
			md5 = getMD5(md5_str);
		} catch (NoSuchAlgorithmException t) {
			log.warn("MD5 error creating class on turnitin");
			throw new SubmissionException("Cannot generate MD5 hash for Turnitin API call", t);
		}

		HttpsURLConnection connection;

		try {
			connection = fetchConnection(apiURL, proxy);
			log.debug("HTTPS Connection made to Turnitin");

			OutputStream outStream = connection.getOutputStream();

			writeBytesToOutputStream(outStream, 
				"uid=", uid, 
				"&cid=", cid, 
				"&aid=", aid, 
				"&cpw=", cpw, 
				"&ctl=", ctl,
				"&diagnostic=", diagnostic, 
				"&encrypt=", encrypt, 
				"&fcmd=", fcmd, 
				"&fid=", fid, 
				"&gmtime=", gmtime, 
				"&said=", said, 
				"&uem=", uem, 
				"&ufn=", ufn, 
				"&uln=", uln,
				"&upw=", upw, 
				"&utp=", utp, 
				"&md5=", md5
			);

			outStream.close();
		}
		catch (IOException t) {
			throw new TransientSubmissionException("Class creation call to Turnitin API failed", t);
		}

		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		} catch (IOException t) {
			throw new TransientSubmissionException ("Cannot get Turnitin response. Assuming call was unsuccessful", t);
		}
		Document document = null;
		try {	
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder  parser = documentBuilderFactory.newDocumentBuilder();
			document = parser.parse(new org.xml.sax.InputSource(in));
		}
		catch (ParserConfigurationException pce){
			log.error("parser configuration error: " + pce.getMessage());
			throw new TransientSubmissionException ("Parser configuration error", pce);
		} catch (Exception t) {
			throw new TransientSubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful", t);
		}


		Element root = document.getDocumentElement();
		String rcode = ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim();
		
		if (((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("20") == 0 || 
				((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim().compareTo("21") == 0 ) {
			log.debug("Create Class successful");						
		} else {
			if ("218".equals(rcode) || "9999".equals(rcode)) {
				throw new TransientSubmissionException("Create Class not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim());
			} else {
				throw new SubmissionException("Create Class not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + ((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim());
			}
		}
	}
	
	/**
	 * @param cid Course ID
	 * @param ctl Course Title
	 * @param assignid Assignment ID
	 * @param assignTitle Assignment Title
	 * @param uem User Email
	 * @param ufn User First Name
	 * @param uln User Last Name
	 * @param upw User Password
	 * @param uid User ID
	 * @param aid Primary Account ID. The account associated with the Turnitin
	 * license.
	 * @param secretKey Shared secret for account
	 * @param said Sub Account ID
	 * @param apiURL API Url. This is usually always 
	 * @param proxy
	 * @return 
	 * @throws SubmissionException
	 * @throws TransientSubmissionException
	 */
	public static Document createAssignment(String cid, String ctl, 
			String assignid, String assignTitle, 
			String uem, String ufn, String uln, String upw, String uid, 
			String aid,
			String secretKey, String said, String apiURL, Proxy proxy ) throws SubmissionException, TransientSubmissionException {

		String diagnostic = "0"; //0 = off; 1 = on

		SimpleDateFormat dform = ((SimpleDateFormat) DateFormat.getDateInstance());
		dform.applyPattern("yyyyMMdd");
		Calendar cal = Calendar.getInstance();
		//set this to yesterday so we avoid timezine probelms etc
		cal.add(Calendar.DAY_OF_MONTH, -1);
		String dtstart = dform.format(cal.getTime());

		//set the due dates for the assignments to be in 5 month's time
		//turnitin automatically sets each class end date to 6 months after it is created
		//the assignment end date must be on or before the class end date

		//TODO use the 'secret' function to change this to longer
		cal.add(Calendar.MONTH, 5);
		String dtdue = dform.format(cal.getTime());

		String encrypt = "0";					//encryption flag
		String fcmd = "2";						//new assignment
		String fid = "4";						//function id
		String utp = "2"; 					//user type 2 = instructor
		String s_view_report = "1";

		String gmtime = getGMTime();
		String assignEnc = assignTitle;
		try {
			if (assignTitle.contains("&")) {
				//log.debug("replacing & in assingment title");
				assignTitle = assignTitle.replace('&', 'n');

			}
			assignEnc = assignTitle;
			log.debug("Assign title is " + assignEnc);

		}
		catch (Exception e) {
			e.printStackTrace();
		}

		String md5_str  = aid + assignEnc + assignid + cid + ctl + diagnostic + dtdue + dtstart + encrypt +
		fcmd + fid + gmtime + said + uem + ufn + uid + uln + upw + utp + secretKey;

		String md5;
		try{
			md5 = getMD5(md5_str);
		} catch (Exception t) {
			log.warn("MD5 error creating assignment on turnitin");
			throw new SubmissionException("Could not generate MD5 hash for \"Create Assignment\" Turnitin API call");
		}

		HttpsURLConnection connection;

		try {		
			connection = fetchConnection(apiURL, proxy);

			log.debug("HTTPS connection made to Turnitin");

			OutputStream outStream = connection.getOutputStream();

			writeBytesToOutputStream(outStream, 
				"aid=",            aid,
				"&assign=",        assignEnc,
				"&assignid=",      assignid,
				"&cid=",           cid,
				"&uid=",           uid,
				"&ctl=",           ctl,
				"&diagnostic=",    diagnostic,
				"&dtdue=",         dtdue,
				"&dtstart=",       dtstart,
				"&encrypt=",       encrypt,
				"&fcmd=",          fcmd,
				"&fid=",           fid,
				"&gmtime=",        gmtime,
				"&s_view_report=", s_view_report,
				"&said=",          said, 
				"&uem=",           uem,
				"&ufn=",           ufn,
				"&uln=",           uln,
				"&upw=",           upw, 
				"&utp=",           utp,
				"&md5=",           md5
			);

			outStream.close();
		} catch (ProtocolException e) {
			throw new TransientSubmissionException("Assignment creation: ProtocolException", e);
		} catch (MalformedURLException e) {
			throw new TransientSubmissionException("Assignment creation: MalformedURLException", e);
		} catch (IOException e) {
			throw new TransientSubmissionException("Assignment creation: IOException", e);
		}
		
		BufferedReader in;
		
			try {
				in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			} catch (IOException e1) {
				throw new TransientSubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful", e1);
			}
		 

		Document document = null;
		try {	
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder  parser = documentBuilderFactory.newDocumentBuilder();
			document = parser.parse(new org.xml.sax.InputSource(in));
		}
		catch (ParserConfigurationException pce){
			log.error("parser configuration error: " + pce.getMessage());
			throw new TransientSubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful", pce);
		} catch (SAXException e) {
			throw new TransientSubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful", e);
		} catch (IOException e) {
			throw new TransientSubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful", e);
		} 	

		Element root = document.getDocumentElement();
		int rcode = new Integer(((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim()).intValue();
		if ((rcode > 0 && rcode < 100) || rcode == 419) {
			log.debug("Create Assignment successful");	
			log.debug("tii returned " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
		} else {
			log.debug("Assignment creation failed with message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
			//log.debug(root);
			throw new TransientSubmissionException("Create Assignment not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
		}
		
		return document;
	}

	private static HttpsURLConnection fetchConnection(String apiURL, Proxy proxy)
			throws MalformedURLException, IOException, ProtocolException {
		HttpsURLConnection connection;
		URL hostURL = new URL(apiURL);
		if (proxy == null) {
			connection = (HttpsURLConnection) hostURL.openConnection();
		} else {
			connection = (HttpsURLConnection) hostURL.openConnection(proxy);
		}
		
		// This actually turns into a POST since we are writing to the
		// resource body. ( You can see this in Webscarab or some other HTTP
		// interceptor.
		connection.setRequestMethod("GET"); 
		
		connection.setDoOutput(true);
		connection.setDoInput(true);
		return connection;
	}
	
	public static String getGMTime() {
		// calculate function2 data
		SimpleDateFormat dform = ((SimpleDateFormat) DateFormat
				.getDateInstance());
		dform.applyPattern("yyyyMMddHH");
		dform.setTimeZone(TimeZone.getTimeZone("GMT"));
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

		String gmtime = dform.format(cal.getTime());
		gmtime += Integer.toString(((int) Math.floor((double) cal
				.get(Calendar.MINUTE) / 10)));

		return gmtime;
	}
	
	public static void writeBytesToOutputStream(OutputStream outStream, String... vargs) throws UnsupportedEncodingException, IOException {
		for (String next: vargs) {
			outStream.write(next.getBytes("UTF-8"));
		}
	}
	
	public static String getMD5(String md5_string) throws NoSuchAlgorithmException {

		MessageDigest md = MessageDigest.getInstance("MD5");

		md.update(md5_string.getBytes());

		// convert the binary md5 hash into hex
		String md5 = "";
		byte[] b_arr = md.digest();

		for (int i = 0; i < b_arr.length; i++) {
			// convert the high nibble
			byte b = b_arr[i];
			b >>>= 4;
			b &= 0x0f; // this clears the top half of the byte
			md5 += Integer.toHexString(b);

			// convert the low nibble
			b = b_arr[i];
			b &= 0x0F;
			md5 += Integer.toHexString(b);
		}

		return md5;
	}

}
