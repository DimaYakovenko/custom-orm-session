package orm;

import javax.sql.DataSource;

public class SessionFactory {

    private DataSource dataSource;

    public SessionFactory(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SessionImpl createSession() {
        return new SessionImpl(dataSource);
    }
}
