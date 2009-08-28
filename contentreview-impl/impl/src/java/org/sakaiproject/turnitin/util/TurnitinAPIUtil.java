package org.sakaiproject.turnitin.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
//import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import sun.net.www.protocol.http.HttpURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.azeckoski.reflectutils.transcoders.XMLTranscoder;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.exception.TransientSubmissionException;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import uk.org.ponder.streamutil.StreamCopyUtil;

/**
 * This is a utility class for wrapping the physical https calls to the
 * Turn It In Service.
 * 
 * @author sgithens
 *
 */
public class TurnitinAPIUtil {
    private static final Log log = LogFactory.getLog(TurnitinAPIUtil.class);

    private static String encodeSakaiTitles(String assignTitle) {
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
        return assignEnc;
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

    @SuppressWarnings({ "unchecked" })
	public static Map packMap(Map map, String... vargs) {
        Map togo = map;
        if (map == null) {
            map = new HashMap();
        }
        if (vargs.length % 2 != 0) {
            throw new IllegalArgumentException("You need to supply an even number of vargs for the key-val pairs.");
        }
        for (int i = 0; i < vargs.length; i+=2) {
            map.put(vargs[i], vargs[i+1]);
        }
        return map;
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
    
    public static Map callTurnitinReturnMap(String apiURL, Map<String,Object> parameters, 
            String secretKey, Proxy proxy) throws TransientSubmissionException, SubmissionException 
    {
        InputStream inputStream = callTurnitinReturnInputStream(apiURL, parameters, secretKey, proxy);
        XMLTranscoder xmlt = new XMLTranscoder();
        return xmlt.decode(StreamCopyUtil.streamToString(inputStream));
    }

    public static Document callTurnitinReturnDocument(String apiURL, Map<String,Object> parameters, 
            String secretKey, Proxy proxy) throws TransientSubmissionException, SubmissionException {
        InputStream inputStream = callTurnitinReturnInputStream(apiURL, parameters, secretKey, proxy);
        
        BufferedReader in;
        in = new BufferedReader(new InputStreamReader(inputStream));
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
        
        return document;
    }
    
    public static InputStream callTurnitinReturnInputStream(String apiURL, Map<String,Object> parameters, 
            String secretKey, Proxy proxy) throws TransientSubmissionException, SubmissionException {
        InputStream togo = null;
        
        if (!parameters.containsKey("fid")) {
            throw new IllegalArgumentException("You must to include a fid in the parameters");
        }
        
        TIIFID fid = TIIFID.getFid(Integer.parseInt((String) parameters.get("fid")));
        
        //if (!parameters.containsKey("gmttime")) {
        parameters.put("gmtime", getGMTime());
        //}

        List<String> sortedkeys = new ArrayList<String>();
        sortedkeys.addAll(parameters.keySet());

        Collections.sort(sortedkeys);

        
        StringBuilder md5sb = new StringBuilder();
        for (int i = 0; i < sortedkeys.size(); i++) {
            if (fid.includeParamInMD5(sortedkeys.get(i))) {
                md5sb.append(parameters.get(sortedkeys.get(i)));
            }
        }

        md5sb.append(secretKey);

        String md5;
        try{
            md5 = getMD5(md5sb.toString());
        } catch (NoSuchAlgorithmException t) {
            log.warn("MD5 error creating class on turnitin");
            throw new SubmissionException("Cannot generate MD5 hash for Turnitin API call", t);
        }

        HttpsURLConnection connection;
        
        try {
            connection = fetchConnection(apiURL, proxy);
            log.debug("HTTPS Connection made to Turnitin");

            OutputStream outStream = connection.getOutputStream();
            
            writeBytesToOutputStream(outStream, sortedkeys.get(0),"=",
                    parameters.get(sortedkeys.get(0)).toString());
            
            for (int i = 1; i < sortedkeys.size(); i++) {
                writeBytesToOutputStream(outStream, "&", sortedkeys.get(i), "=", 
                        parameters.get(sortedkeys.get(i)).toString());
            }
            
            writeBytesToOutputStream(outStream, "&md5=", md5);
        
            outStream.close();
            
            togo = connection.getInputStream();
        }
        catch (IOException t) {
            throw new TransientSubmissionException("Class creation call to Turnitin API failed", t);
        }
        
        return togo;
        
    }

}