package orm;

public interface Session {

    <E> E find(Class<E> entityType, Object id);
}
