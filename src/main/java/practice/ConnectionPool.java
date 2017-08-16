package practice;

import lombok.SneakyThrows;
import lombok.val;

import java.sql.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ConnectionPool {

    private BlockingQueue<Connection> connectionQueue;

    private String driverName;
    private String url;
    private String user;
    private String password;
    private int poolSize;

    private ConnectionPool() {
        DBResourceManager dbResourseManager = DBResourceManager.getInstance();
        this.driverName = dbResourseManager.getValue(DBParameter.DB_DRIVER);
        this.url = dbResourseManager.getValue(DBParameter.DB_URL);
        this.user = dbResourseManager.getValue(DBParameter.DB_USER);

        this.password = dbResourseManager.getValue(DBParameter.DB_PASSWORD);

        try {
            this.poolSize = Integer.parseInt(dbResourseManager
                    .getValue(DBParameter.DB_POLL_SIZE));
        } catch (NumberFormatException e) {
            poolSize = 5;
        }

        try {
            Class.forName(driverName);
            connectionQueue = new ArrayBlockingQueue<>(poolSize);
            for (int i = 0; i < poolSize; i++)
                connectionQueue.add(getConnection());
        } catch (ClassNotFoundException e) {
            throw new ConnectionPoolException("Can't find database driver class", e);
        }
    }

    @SneakyThrows
    private PooledConnection getConnection() {
        try {
            val connection = DriverManager.getConnection(url, user, password);
            return () -> connection;
        } catch (SQLException e) {
            throw new ConnectionPoolException("SQLException in ConnectionPool", e);
        }
    }

    public void dispose() {
        clearConnectionQueue();
    }

    private void clearConnectionQueue() {
        try {
            closeConnectionsQueue(connectionQueue);
        } catch (SQLException e) {
            // logger.log(Level.ERROR, "Error closing the connection.", e);
        }
    }

    public Connection takeConnection() throws ConnectionPoolException {
        Connection connection = null;
        try {
            connection = connectionQueue.take();
        } catch (InterruptedException e) {
            throw new ConnectionPoolException(
                    "Error connecting to the data source.", e);
        }
        return connection;
    }

    public void closeConnection(Connection con, Statement st, ResultSet rs) {
        try {
            con.close();
        } catch (SQLException e) {
            // logger.log(Level.ERROR, "Connection isn't return to the pool.");
        }
        try {
            rs.close();
        } catch (SQLException e) {
            // logger.log(Level.ERROR, "ResultSet isn't closed.");
        }
        try {
            st.close();
        } catch (SQLException e) {
            // logger.log(Level.ERROR, "Statement isn't closed.");
        }
    }

    public void closeConnection(Connection con, Statement st) {
        try {
            con.close();
        } catch (SQLException e) {
            // logger.log(Level.ERROR, "Connection isn't return to the pool.");
        }
        try {
            st.close();
        } catch (SQLException e) {
            // logger.log(Level.ERROR, "Statement isn't closed.");
        }
    }

    private void closeConnectionsQueue(BlockingQueue<Connection> queue)
            throws SQLException {
        Connection connection;
        while ((connection = queue.poll()) != null) {
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            ((PooledConnection) connection).reallyClose();
        }
    }
}