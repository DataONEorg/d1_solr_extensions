/**
 * This work was created by participants in the DataONE project, and is
 * jointly copyrighted by participating institutions in DataONE. For 
 * more information on DataONE, see our web site at http://dataone.org.
 *
 *   Copyright ${year}
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 * 
 * $Id$
 */

package org.dataone.cn.auth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.dataone.configuration.Settings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * mostly copied from a Test case in the BouncyCastle distribution
 * @author waltz
 */
@Component
@Qualifier("x509CertificateGenerator")
public class X509CertificateGenerator {

    /** Our log4j logger. */
    private static Logger logger = Logger.getLogger(X509CertificateGenerator.class);
    private static final String BC = org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
    
    // the next two constants could be variables to the method below
    private static final int validityDays = 36525;
    SecureRandom sr = null;;
    public X509CertificateGenerator() {
        sr = new SecureRandom();
        sr.setSeed(System.currentTimeMillis());
        sr.nextInt();
        if ((Security.getProvider(BC) == null) || (Security.getProvider(BC).isEmpty())) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
    public  KeyPair createKeys () throws NoSuchAlgorithmException {

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024, sr);
        KeyPair keypair = keyGen.generateKeyPair();
        return keypair;
    }

    public X509Certificate generateDataOneCert(String cn) throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException{
        KeyPair keyPair = createKeys ();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        //
        // distinguished name table.
        //
        X500NameBuilder builder = new X500NameBuilder(RFC4519Style.INSTANCE);

        builder.addRDN(RFC4519Style.cn, cn);
        builder.addRDN(RFC4519Style.dc, "dataone");
        builder.addRDN(RFC4519Style.dc, "org");
        return generateCACert(publicKey, privateKey, builder);
    }
    public X509Certificate generateCACert(PublicKey pubKey, PrivateKey privKey, X500NameBuilder builder) throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException {
        
        Calendar now = Calendar.getInstance();
        now.add(Calendar.DAY_OF_YEAR, validityDays);
        Date startDate = new Date(System.currentTimeMillis());
        Date expireDate = new Date(now.getTimeInMillis());
        // don't know if this is really needed, but what the hey
        BigInteger dateInteger = BigInteger.valueOf(System.currentTimeMillis());
        BigInteger bigSerialNumber = BigInteger.valueOf(sr.nextLong());
        bigSerialNumber.add(dateInteger);

        ContentSigner sigGen = new JcaContentSignerBuilder("MD5WithRSAEncryption").setProvider(BC).build(privKey);
        X509v1CertificateBuilder certGen1 = new JcaX509v1CertificateBuilder(builder.build(), bigSerialNumber, startDate, expireDate, builder.build(), pubKey);

        X509Certificate cert = new JcaX509CertificateConverter().setProvider(BC).getCertificate(certGen1.build(sigGen));

        cert.checkValidity(new Date());

        cert.verify(pubKey);

        // System.out.println(cert);
        if (!cert.getIssuerDN().equals(cert.getSubjectDN())) {
            logger.error("name comparison fails");
        }
        logger.info(cert.getSubjectDN().getName());
        return cert;
    }

    public X509Certificate generateSelfSignedCertificate(PublicKey publicKey, PrivateKey caPrivateKey, X509Certificate caCert,X500NameBuilder builder) throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException, IOException {

        Calendar now = Calendar.getInstance();
        now.add(Calendar.DAY_OF_YEAR, validityDays);
        Date startDate = new Date(System.currentTimeMillis());
        Date expireDate = new Date(now.getTimeInMillis());
        // don't know if this is really needed, but what the hey
        BigInteger dateInteger = BigInteger.valueOf(System.currentTimeMillis());
        BigInteger bigSerialNumber = BigInteger.valueOf(sr.nextLong());
        bigSerialNumber.add(dateInteger);


        ContentSigner sigGen = new JcaContentSignerBuilder("MD5WithRSAEncryption").setProvider(BC).build(caPrivateKey);
        X509v3CertificateBuilder certGen1 = new JcaX509v3CertificateBuilder(builder.build(), bigSerialNumber, startDate, expireDate, builder.build(), publicKey).addExtension(X509Extension.authorityKeyIdentifier, false, new AuthorityKeyIdentifierStructure(caCert));
        X509Certificate cert = new JcaX509CertificateConverter().setProvider(BC).getCertificate(certGen1.build(sigGen));

        cert.checkValidity(new Date());

        cert.verify(caCert.getPublicKey());

        // System.out.println(cert);
        if (!cert.getIssuerDN().equals(cert.getSubjectDN())) {
            logger.error("name comparison fails");
        }
        logger.info("Self Signed Certificat is " + cert.getSubjectDN().getName());
        return cert;
    }
    public void storeSelfSignedCertificate(String subjectCN) throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException, IOException, KeyStoreException {
        KeyPair caKeyPair = createKeys ();
        PublicKey caPublicKey = caKeyPair.getPublic();
        PrivateKey caPrivateKey = caKeyPair.getPrivate();

        //
        // distinguished name table.
        //
        X500NameBuilder caBuilder = new X500NameBuilder(RFC4519Style.INSTANCE);

        caBuilder.addRDN(RFC4519Style.cn, "cn.dataone.org Mock CA");
        caBuilder.addRDN(RFC4519Style.dc, "dataone");
        caBuilder.addRDN(RFC4519Style.dc, "org");
        X509Certificate caCert = generateCACert(caPublicKey, caPrivateKey, caBuilder);

        KeyPair keyPair = createKeys ();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        //
        // distinguished name table.
        //
        X500NameBuilder builder = new X500NameBuilder(RFC4519Style.INSTANCE);

        builder.addRDN(RFC4519Style.dc, "org");
        builder.addRDN(RFC4519Style.dc, "dataone");
        builder.addRDN(RFC4519Style.cn, subjectCN);

        X509Certificate cert = generateSelfSignedCertificate(publicKey, caPrivateKey, caCert, builder);

        storeCACert("dataoneCA", caPrivateKey, caCert);

        File pemFileLocation = new File(locateCertificate());
        FileWriter fileWriter = new FileWriter(pemFileLocation);
        PEMWriter pemWriter = new PEMWriter(fileWriter);
        pemWriter.writeObject(privateKey);
        pemWriter.writeObject(cert);
        pemWriter.flush();
        pemWriter.close();
    }
    public void storeCACert(String caAlias,PrivateKey caPrivateKey, X509Certificate caCert) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            File caFile = new File("/tmp/caD1certs");
            if (!caFile.exists()) {
                caFile.createNewFile();
                trustStore.load(null, "whatnot".toCharArray());
            } else {
            InputStream caInStream = new FileInputStream(caFile);
            trustStore.load(caInStream, "whatnot".toCharArray());
            }
             Certificate[] chain = {caCert};
            trustStore.setKeyEntry(caAlias,
			caPrivateKey, "whatnot".toCharArray(),chain);
            OutputStream caOutStream = new FileOutputStream(caFile);
            trustStore.store(caOutStream, "whatnot".toCharArray());
    }
    public X509Certificate getCACert(String caAlias) {
        X509Certificate caCert = null;
        KeyStore trustStore = null;
        try {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            File caFile = new File("/tmp/caD1certs");
            InputStream caStream = new FileInputStream(caFile);
            trustStore.load(caStream, "whatnot".toCharArray());
            caCert = (X509Certificate) trustStore.getCertificate(caAlias);
        } catch (KeyStoreException e) {
            logger.error(e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
        	logger.error(e.getMessage(), e);
        } catch (CertificateException e) {
        	logger.error(e.getMessage(), e);
        } catch (FileNotFoundException e) {
        	logger.error(e.getMessage(), e);
        } catch (IOException e) {
        	logger.error(e.getMessage(), e);
        }
        return caCert;
    }
   /**
     * Locate the default certificate location
     * http://www.cilogon.org/cert-howto#TOC-Finding-CILogon-Certificates
     * @return
     * @throws FileNotFoundException
     */
    private String locateCertificate() throws FileNotFoundException {
    	StringBuffer location = new StringBuffer();

    	// the tmp dir
    	String tmp = System.getProperty("tmpdir");
    	if (tmp == null) {
    		tmp = "/tmp";
    	}

    	// UID
    	String uid = null;
    	try {
    		// get the user id from *nix systems
    		Process process = Runtime.getRuntime().exec("id -u");
    		int ret = process.waitFor();
    		if (ret == 0) {
    			InputStream stream = process.getInputStream();
    			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    			String result = reader.readLine();
    			// verify it is a number
    			int testUid = Integer.parseInt(result);
    			uid = String.valueOf(testUid);
    		}
    	} catch (Exception e) {
			logger.warn("No UID found, using user.name");
		}
    	if (uid == null) {
    		uid = System.getProperty("user.name");
    	}
		location.append(tmp);
		location.append("/");
		location.append("x509up_u");
		location.append(uid);

		logger.debug("Calculated certificate location: " + location.toString());


    	return location.toString();
    }
    public void cleanUpFiles() throws FileNotFoundException {
         File pemFileLocation = new File(locateCertificate());
         pemFileLocation.delete();
         File caFile = new File("/tmp/caD1certs");
         caFile.delete();

    }
}
