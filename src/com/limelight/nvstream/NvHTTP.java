package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Stack;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class NvHTTP {
	private String host;
	private String macAddress;
	
	public static final int PORT = 47989;
	
	public NvHTTP(String host, String macAddress)
	{
		this.host = host;
		this.macAddress = macAddress;
	}
	
	private String getXmlString(InputStream in, String attribute) throws XmlPullParserException, IOException
	{
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        
        xpp.setInput(new InputStreamReader(in));
        int eventType = xpp.getEventType();
        Stack<String> currentTag = new Stack<String>();

        while (eventType != XmlPullParser.END_DOCUMENT)
        {
            if (eventType == XmlPullParser.START_TAG) {
            	currentTag.push(xpp.getName());
            	for (int i = 0; i < xpp.getAttributeCount(); i++)
            	{
            		if (xpp.getAttributeName(i).equals(attribute))
            			return xpp.getAttributeValue(i);
            	}
            } else if (eventType == XmlPullParser.END_TAG) {
                currentTag.pop();
            } else if (eventType == XmlPullParser.TEXT) {
            	if (currentTag.peek().equals(attribute)) {
                    return xpp.getText();
            	}
            }
            eventType = xpp.next();
        }
        
        return null;
	}
	
	private InputStream openHttpConnection(String url) throws IOException
	{
		return new URL(url).openConnection().getInputStream();
	}
	
	public String getAppVersion() throws XmlPullParserException, IOException
	{
		InputStream in = openHttpConnection("http://"+host+":"+PORT+"/appversion");
		return getXmlString(in, "appversion");
	}
	
	public boolean getPairState() throws IOException, XmlPullParserException
	{
		InputStream in = openHttpConnection("http://"+host+":"+PORT+"/pairstate?mac="+macAddress);
		String paired = getXmlString(in, "paired");
		return Integer.valueOf(paired) != 0;
	}
	
	public int getSessionId() throws IOException, XmlPullParserException
	{
		/* Pass the model (minus spaces) as the device name */
		String deviceName = android.os.Build.MODEL;
		deviceName = deviceName.replace(" ", "");
		InputStream in = openHttpConnection("http://"+host+":"+PORT+"/pair?mac="+macAddress+"&devicename="+deviceName);
		String sessionId = getXmlString(in, "sessionid");
		return Integer.valueOf(sessionId);
	}
	
	public int getSteamAppId(int sessionId) throws IOException, XmlPullParserException
	{
		InputStream in = openHttpConnection("http://"+host+":"+PORT+"/applist?session="+sessionId);
		String appId = getXmlString(in, "ID");
		return Integer.valueOf(appId);
	}
	
	// Returns gameSession XML attribute
	public int launchApp(int sessionId, int appId) throws IOException, XmlPullParserException
	{
		InputStream in = openHttpConnection("http://"+host+":"+PORT+"/launch?session="+sessionId+"&appid="+appId);
		String gameSession = getXmlString(in, "gamesession");
		return Integer.valueOf(gameSession);
	}
}
