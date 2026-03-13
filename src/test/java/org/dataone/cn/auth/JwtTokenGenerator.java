package org.dataone.cn.auth;

import com.nimbusds.jose.JOSEException;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.dataone.configuration.Settings;
import org.dataone.portal.TokenGenerator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;

/**
 * A utility class to use d1_portal to generate JWT tokens
 */
public class JwtTokenGenerator {
    private static final String PRIVATE_KEY_PATH = "target/test_private_key";
    private static final String PUBLIC_KEY_PATH = "target/test_public_key";
    private static volatile JwtTokenGenerator tokenGenerator = null;


    /**
     * Private constructor
     */
    private JwtTokenGenerator()
        throws NoSuchAlgorithmException, IOException, CertificateException, SignatureException,
        OperatorCreationException, InvalidKeyException, NoSuchProviderException {
        clearCredentialFiles();
        X509CertificateGenerator certificateGenerator = new X509CertificateGenerator();
        KeyPair keyPair = certificateGenerator.createKeys();
        PrivateKey privateKey = keyPair.getPrivate();
        savePrivateKey(privateKey, PRIVATE_KEY_PATH);
        // Set the file path to the Setting. D1_portal will use it to locate the private key to
        // sign tokens
        Settings.getConfiguration().setProperty("cn.server.privatekey.filename", PRIVATE_KEY_PATH);
        PublicKey publicKey = keyPair.getPublic();
        X500NameBuilder caBuilder = new X500NameBuilder(RFC4519Style.INSTANCE);
        caBuilder.addRDN(RFC4519Style.cn, "Mock_CA");
        caBuilder.addRDN(RFC4519Style.dc, "dataone");
        caBuilder.addRDN(RFC4519Style.dc, "org");
        X509Certificate caCertificate = certificateGenerator.generateCACert(publicKey, privateKey
            , caBuilder);
        saveCertificate(caCertificate, PUBLIC_KEY_PATH);
        // Set the file path to the Setting. D1_portal will use it to locate the certificate to
        // verify tokens
        Settings.getConfiguration().setProperty("cn.server.publiccert.filename", PUBLIC_KEY_PATH);
    }

    /**
     * Get the singleton JwtTokenGenerator class
     * @return the JwtTokenGenerator object
     * @throws NoSuchAlgorithmException
     */
    public static JwtTokenGenerator getInstance()
        throws NoSuchAlgorithmException, IOException, CertificateException, SignatureException,
        OperatorCreationException, InvalidKeyException, NoSuchProviderException {
        synchronized (JwtTokenGenerator.class) {
            if (tokenGenerator == null) {
                tokenGenerator = new JwtTokenGenerator();
            }
        }
        return tokenGenerator;
    }

    /**
     * Generate a token from user with the given userId and fullName by d1_portal
     * @param userId  the user id of the token
     * @param fullName  the full name of the token
     * @return a token with the given info
     */
    public String generateToken(String userId, String fullName)
        throws IOException, ParseException, JOSEException {
        return TokenGenerator.getInstance().getJWT(userId, fullName);
    }

    private void savePrivateKey(PrivateKey privateKey, String fileName) throws IOException {
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(fileName))) {
            writer.writeObject(privateKey);
        }
    }

    private void saveCertificate(X509Certificate cert, String fileName) throws IOException {
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(fileName))) {
            writer.writeObject(cert);
        }
    }

    private void clearCredentialFiles() {
        File privateKey = new File(PRIVATE_KEY_PATH);
        privateKey.delete();
        File publicKey = new File(PUBLIC_KEY_PATH);
        publicKey.delete();
    }

}
