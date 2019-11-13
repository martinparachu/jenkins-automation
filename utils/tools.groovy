import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class tools {
	def doGetRequest(urlString){
		// Disable SSl validation
		def nullTrustManager = [
			checkClientTrusted: { chain, authType ->  },
			checkServerTrusted: { chain, authType ->  },
			getAcceptedIssuers: { null }
		]
		def nullHostnameVerifier = [
			verify: { hostname, session -> true }
		]
		SSLContext sc = SSLContext.getInstance("SSL")
		sc.init(null, [nullTrustManager as X509TrustManager] as TrustManager[], null)
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
		HttpsURLConnection.setDefaultHostnameVerifier(nullHostnameVerifier as HostnameVerifier)

		// Create URL
		Boolean enableAuthentication = false
		URL url = urlString.toURL()

		// Open connection
		URLConnection connection = url.openConnection()
		if (enableAuthentication) {
			String username = "dummy_user"
			String password = "dummy_pass"

			// Create authorization header using Base64 encoding
			String userpass = username + ":" + password;
			String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());

			// Set authorization header
			connection.setRequestProperty("Authorization", basicAuth)
		}

		// Open input stream
		InputStream inputStream = connection.getInputStream()

		def jsonData = new groovy.json.JsonSlurper().parseText(inputStream.text)
		// Close the stream
		inputStream.close()

		return jsonData
	}
}
