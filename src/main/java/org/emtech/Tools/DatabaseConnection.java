package org.emtech.Tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Slf4j
@Component
public class DatabaseConnection {

    Configurations cf = new Configurations();
    String key = cf.getProperties().getProperty("enc.key");
    String initVector = cf.getProperties().getProperty("enc.initVector");

    private final String dbclass = cf.getProperties().getProperty("db.class");
    private final String ip = cf.getProperties().getProperty("db.ip");
    private final String port = cf.getProperties().getProperty("db.port");
    private final String databaseName = cf.getProperties().getProperty("db.database");
    private final String username = cf.getProperties().getProperty("db.username");
    private final String password = cf.getProperties().getProperty("db.password");

    public Connection dbConnection() {
        Connection conn = null;
        try {
            String driver = Encryptor.decrypt(key, initVector, dbclass);
            String host = Encryptor.decrypt(key, initVector, ip);
            String portNumber = Encryptor.decrypt(key, initVector, port);
            String sid = Encryptor.decrypt(key, initVector, databaseName);
           // String sid = Encryptor.decrypt(key, initVector, databaseName);
            String url = "jdbc:oracle:thin:@" + host + ":" + portNumber + ":" + sid;
           // String url = "jdbc:mariadb://" + host + ":" + portNumber + "/" + sid;
            Class.forName(driver);
            String uname = Encryptor.decrypt(key, initVector, username);
            String pass = Encryptor.decrypt(key, initVector, password);

            conn = DriverManager.getConnection(url, uname, pass);
        } catch (Exception e) {
            log.error("DB Error {}", e.getMessage(), e);
        }
        return conn;
    }

    public void closeConn(Connection con) {
        try {
            if (con != null) con.close();
        } catch (Exception ex) {
            log.error("Error closing connection: {}", ex.getMessage());
        }
    }
}
