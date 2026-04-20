# d1_solr_extensions Setup Guide

To get **`d1_solr_extensions`** working properly, you need to complete the following configuration steps.

## 1. Update Solr Security Policy

Edit the file `$SOLR_HOME/server/etc/security.policy` and add the following permissions:
```
permission java.security.SecurityPermission "putProviderProperty.BC";  
permission java.net.URLPermission "https://cn.dataone.org/cn/-" "GET";
```

**Note:** The `java.net.URLPermission` value may vary depending on the Coordinating Node (CN) environment you are connecting to (e.g., production, staging, or sandbox). Make sure to update the URL accordingly for your target CN instance.

## 2. Configure Environment Variables

Set the following environment variables:

- `D1_CN_SOLR_ADMIN_TOKEN` – The token used to enable the DataONE CN admin subject privilege in search.
- `D1_CN_URL` – The Coordinating Node (CN) URL. Default: `https://cn.dataone.org/cn`
- `D1_CN_ADMINS` (Optional) – A semicolon-separated list of subjects that have DataONE CN privileges.
  - Example: `http://orcid.org/0000-0001-5109-3700;http://orcid.org/0000-0002-9079-593X`

## Notes

- Make sure environment variables are available to the Solr runtime (e.g., set in your shell profile or service configuration).  
- Restart Solr after making these changes.