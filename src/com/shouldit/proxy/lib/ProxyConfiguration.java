package com.shouldit.proxy.lib;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.conn.util.InetAddressUtils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.webkit.URLUtil;

import com.shouldit.proxy.lib.APLConstants.CheckStatusValues;
import com.shouldit.proxy.lib.APLConstants.ProxyStatusErrors;
import com.shouldit.proxy.lib.APLConstants.ProxyStatusProperties;
import com.shouldit.proxy.lib.reflection.ReflectionUtils;
import com.shouldit.proxy.lib.reflection.android.ProxySetting;

public class ProxyConfiguration implements Comparable<ProxyConfiguration>
{
	public static final String TAG = "ProxyConfiguration";

	public Context context;
	public ProxyStatus status;
	public AccessPoint ap;
	public NetworkInfo currentNetworkInfo;

	public ProxySetting proxySetting;
	private String proxyHost;
	private Integer proxyPort;
	public String proxyExclusionList;
	
	public int deviceVersion;
	private ConnectivityManager connManager;

	public Proxy getProxy()
	{
		if (isProxyEnabled())
			return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxyHost, proxyPort));
		else 
			return Proxy.NO_PROXY;
	}
	
	public void setProxyHost(String host)
	{
		proxyHost = host;
	}
	
	public void setProxyPort(int port)
	{
		proxyPort = port;
	}

	public ProxyConfiguration(Context ctx, ProxySetting proxyEnabled, String host, Integer port, String exclusionList, WifiConfiguration wifiConf)
	{
		context = ctx;

		proxySetting = proxyEnabled;
  		proxyHost = host;
  		proxyPort = port;
		proxyExclusionList = exclusionList;

		connManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
		currentNetworkInfo = connManager.getActiveNetworkInfo();

		if (wifiConf != null)
			ap = new AccessPoint(wifiConf);

		deviceVersion = Build.VERSION.SDK_INT;
		status = new ProxyStatus();
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Proxy toggle: %s\n", proxySetting.toString()));
		sb.append(String.format("Proxy: %s\n", toShortString()));
		sb.append(String.format("Is current network: %B\n", isCurrentNetwork()));
		sb.append(String.format("Is Proxy address valid: %B\n", isProxyValidAddress()));
		sb.append(String.format("Is Proxy reachable: %B\n", isProxyReachable()));
		sb.append(String.format("Is WEB reachable: %B\n", isWebReachable(60000)));

		if (currentNetworkInfo != null)
		{
			sb.append(String.format("Network Info: %s\n", currentNetworkInfo));
		}

		if (ap != null && ap.wifiConfig != null)
			sb.append(String.format("Wi-Fi Configuration Info: %s\n", ap.wifiConfig.SSID.toString()));

		return sb.toString();
	}

	public Boolean isCurrentNetwork()
	{
		if (currentNetworkInfo != null && this.getSSID() != null && this.getSSID().equals(currentNetworkInfo.getExtraInfo()))
			return true;
		else
			return false;
	}

	public String toShortString()
	{
		if (proxySetting == ProxySetting.NONE ||
				proxySetting == ProxySetting.UNASSIGNED)
		{
			return Proxy.NO_PROXY.toString();
		}

		return String.format("%s:%d", proxyHost,proxyPort);	
	}

	public Proxy.Type getProxyType()
	{
		return getProxy().type();
	}

	/**
	 * Can take a long time to execute this task. - Check if the proxy is
	 * enabled - Check if the proxy address is valid - Check if the proxy is
	 * reachable (using a PING) - Check if is possible to retrieve an URI
	 * resource using the proxy
	 * */
	public void acquireProxyStatus(int timeout)
	{
		status.clear();
		status.startchecking();
		broadCastUpdatedStatus();

		LogWrapper.d(TAG, "Checking if proxy is enabled ...");
		if (!isProxyEnabled())
		{
			LogWrapper.e(TAG, "PROXY NOT ENABLED");
			status.add(ProxyStatusProperties.PROXY_ENABLED, CheckStatusValues.CHECKED, false);
		}
		else
		{
			LogWrapper.i(TAG, "PROXY ENABLED");
			status.add(ProxyStatusProperties.PROXY_ENABLED, CheckStatusValues.CHECKED, true);
		}

		broadCastUpdatedStatus();

		LogWrapper.d(TAG, "Checking if proxy is valid address ...");
		if (!isProxyValidAddress())
		{
			LogWrapper.e(TAG, "PROXY NOT VALID ADDRESS");
			status.add(ProxyStatusProperties.PROXY_VALID_ADDRESS, CheckStatusValues.CHECKED, false);
		}
		else
		{
			LogWrapper.i(TAG, "PROXY VALID ADDRESS");
			status.add(ProxyStatusProperties.PROXY_VALID_ADDRESS, CheckStatusValues.CHECKED, true);
		}

		broadCastUpdatedStatus();

		LogWrapper.d(TAG, "Checking if proxy is reachable ...");
		if (!isProxyReachable())
		{
			LogWrapper.e(TAG, "PROXY NOT REACHABLE");
			status.add(ProxyStatusProperties.PROXY_REACHABLE, CheckStatusValues.CHECKED, false);
		}
		else
		{
			LogWrapper.i(TAG, "PROXY REACHABLE");
			status.add(ProxyStatusProperties.PROXY_REACHABLE, CheckStatusValues.CHECKED, true);
		}

		broadCastUpdatedStatus();

		LogWrapper.d(TAG, "Checking if web is reachable ...");
		if (!isWebReachable(timeout))
		{
			LogWrapper.e(TAG, "WEB NOT REACHABLE");
			status.add(ProxyStatusProperties.WEB_REACHABLE, CheckStatusValues.CHECKED, false);
		}
		else
		{
			LogWrapper.i(TAG, "WEB REACHABLE");
			status.add(ProxyStatusProperties.WEB_REACHABLE, CheckStatusValues.CHECKED, true);
		}

		broadCastUpdatedStatus();
	}

	private void broadCastUpdatedStatus()
	{
		LogWrapper.d(TAG, "Sending broadcast intent: " + APLConstants.APL_UPDATED_PROXY_STATUS_CHECK);
		Intent intent = new Intent(APLConstants.APL_UPDATED_PROXY_STATUS_CHECK);
		intent.putExtra(APLConstants.ProxyStatus, status);
		context.sendBroadcast(intent);
	}

	public ProxyStatusErrors getMostRelevantProxyStatusError()
	{
		if (!status.getEnabled().result)
			return ProxyStatusErrors.PROXY_NOT_ENABLED;

		if (status.getWeb_reachable().result)
		{
			// If the WEB is reachable, the proxy is OK!
			return ProxyStatusErrors.NO_ERRORS;
		}
		else
		{
			if (status.getProxy_reachable().result)
			{
				return ProxyStatusErrors.WEB_NOT_REACHABLE;
			}
			else
			{
				if (status.getValid_address().result)
				{
					return ProxyStatusErrors.PROXY_NOT_REACHABLE;
				}
				else
					return ProxyStatusErrors.PROXY_ADDRESS_NOT_VALID;
			}
		}
	}

	private Boolean isProxyEnabled()
	{
		Boolean result = false;
		
		if (Build.VERSION.SDK_INT >= 12)
		{
			// On API version > Honeycomb 3.1 (HONEYCOMB_MR1)
			// Proxy is disabled by default on Mobile connection
			if (currentNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE)
				return false;
		}

		if (proxySetting == ProxySetting.UNASSIGNED || proxySetting == ProxySetting.NONE)
		{
			result = false;
		}
		else
		{
			if (proxyHost != null && proxyPort != null)
			{
				result = true; // HTTP or SOCKS proxy
			}
			else
			{
				result = false;
			}
		}
		
		return result;
	}

	private boolean isProxyValidAddress()
	{
		try
		{
			String proxyHost = getProxyHostString();

			if (proxyHost != null)
			{

				if (InetAddressUtils.isIPv4Address(proxyHost) || InetAddressUtils.isIPv6Address(proxyHost) || InetAddressUtils.isIPv6HexCompressedAddress(proxyHost) || InetAddressUtils.isIPv6StdAddress(proxyHost))
				{
					return true;
				}

				if (URLUtil.isNetworkUrl(proxyHost))
				{
					return true;
				}

				if (URLUtil.isValidUrl(proxyHost))
				{
					return true;
				}

				// Test REGEX for Hostname validation
				// http://stackoverflow.com/questions/106179/regular-expression-to-match-hostname-or-ip-address
				//
				String ValidHostnameRegex = "^(([a-zA-Z]|[a-zA-Z][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z]|[A-Za-z][A-Za-z0-9\\-]*[A-Za-z0-9])$";
				Pattern pattern = Pattern.compile(ValidHostnameRegex);
				Matcher matcher = pattern.matcher(proxyHost);

				if (matcher.find())
				{
					return true;
				}
			}
			else
			{
				return false;
			}

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * Try to PING the HOST specified in the current proxy configuration
	 * */
	private Boolean isProxyReachable()
	{
		if (getProxy() != null && getProxyType() != Proxy.Type.DIRECT)
			return ProxyUtils.isHostReachable(getProxy());
		else
			return false;
	}

	/**
	 * Try to download a webpage using the current proxy configuration
	 * */
	public static int DEFAULT_TIMEOUT = 60000; // 60 seconds

	private Boolean isWebReachable()
	{
		return isWebReachable(DEFAULT_TIMEOUT);
	}

	private Boolean isWebReachable(int timeout)
	{
		return ProxyUtils.isWebReachable(this, timeout);
	}

	public String getProxyHostString()
	{
		return proxyHost;
	}

	public String getProxyIPHost()
	{
		return proxyHost;
	}
	
	public String getProxyHost()
	{
		return proxyHost;
	}

	public Integer getProxyPort()
	{
		return proxyPort;
	}

	public CheckStatusValues getCheckingStatus()
	{
		return status.getCheckingStatus();
	}

	public int getNetworkType()
	{
		return currentNetworkInfo.getType();
	}

	@Override
	public int compareTo(ProxyConfiguration another)
	{
		int result = 0;

		if (currentNetworkInfo != null && another.currentNetworkInfo != null)
		{
			if (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI)
			{
				if (another.currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI)
				{
					result = ap.compareTo(another.ap);
					if (result == 0)
					{
						if (getProxy() != another.getProxy())
						{
							result = getProxy().toString().compareTo(another.getProxy().toString());
						}
					}
				}
				else
				{
					result = -1;
				}
			}
			else
			{
				if (another.currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI)
				{
					result = +1;
				}
				else
				{
					result = 0; // Both are mobile or no connection 
				}
			}
		}

		return result;
	}

	public String getAPDescription(Context ctx)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(ap.getSecurityString(ctx, false));
		return sb.toString();
	}

	public String getSSID()
	{
		if (ap != null && ap.wifiConfig != null && ap.wifiConfig.SSID != null)
		{
			return ap.wifiConfig.SSID;
		}
		else
			return null;
	}

	public boolean isValidConfiguration()
	{
		if (ap != null)
			return true;
		else
			return false;
	}

	@Deprecated
	@TargetApi(12)
	public void writeConfigurationToDevice()
	{
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

		try
		{
			Field proxySettingsField = ap.wifiConfig.getClass().getField("proxySettings");
			proxySettingsField.set(ap.wifiConfig, (Object) proxySettingsField.getType().getEnumConstants()[proxySetting.ordinal()]);
			Object proxySettings = proxySettingsField.get(ap.wifiConfig);
			int ordinal = ((Enum) proxySettings).ordinal();
			if (ordinal != proxySetting.ordinal())
				throw new Exception("Cannot set proxySettings variable");

			Field linkPropertiesField = ap.wifiConfig.getClass().getField("linkProperties");
			Object linkProperties = linkPropertiesField.get(ap.wifiConfig);
			Field mHttpProxyField = ReflectionUtils.getField(linkProperties.getClass().getDeclaredFields(), "mHttpProxy");
			mHttpProxyField.setAccessible(true);

			if (proxySetting == ProxySetting.NONE || 
					proxySetting == ProxySetting.UNASSIGNED)
			{
				mHttpProxyField.set(linkProperties, null);
			}
			else if (proxySetting == ProxySetting.STATIC)
			{
				Class ProxyPropertiesClass = mHttpProxyField.getType();
				
				Integer port = getProxyPort();
				if (port == null)
				{
					Constructor constr = ProxyPropertiesClass.getConstructors()[0];
					Object ProxyProperties = constr.newInstance((Object) null);
					mHttpProxyField.set(linkProperties, ProxyProperties);
				}
				else
				{
					Constructor constr = ProxyPropertiesClass.getConstructors()[1];
					Object ProxyProperties = constr.newInstance(getProxyHostString(), port, proxyExclusionList);
					mHttpProxyField.set(linkProperties, ProxyProperties);
				}
			}

			Object mHttpProxy = mHttpProxyField.get(linkProperties);
			mHttpProxy = mHttpProxyField.get(linkProperties);
		
			int result = wifiManager.updateNetwork(ap.wifiConfig);
			if (result == -1)
				throw new Exception("Can't update network configuration");
			
			LogWrapper.d(TAG, "Sending broadcast intent: " + APLConstants.APL_UPDATED_PROXY_CONFIGURATION);
			Intent intent = new Intent(APLConstants.APL_UPDATED_PROXY_CONFIGURATION);
			context.sendBroadcast(intent);
		}
		catch (IllegalArgumentException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IllegalAccessException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (SecurityException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (NoSuchFieldException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}