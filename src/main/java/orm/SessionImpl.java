package orm;

import annotation.Column;
import annotation.Table;
import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class SessionImpl implements Session, AutoCloseable {
    private DataSource dataSource;
    private Map<EntityKey, Object> cache = new HashMap<>();
    private Map<EntityKey, List<Object>> snapshotCopy = new HashMap<>();

    public SessionImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <E> E find(Class<E> entityType, Object id) {
        final EntityKey<Class<E>, Object> key = new EntityKey<>(entityType, id);
        if (cache.containsKey(key)) {
            return ((E) cache.get(key));
        }
        try (Connection connection = dataSource.getConnection()) {
            String tableName = getTableName(entityType);
            String selectSql = "SELECT * from " + tableName + " where id = ?";
            try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                statement.setObject(1, id);
                final ResultSet resultSet = statement.executeQuery();
                final E fetchedEntity = processResultSet(resultSet, entityType);
                cache.put(key, fetchedEntity);
                addSnapshotCopy(key, fetchedEntity, snapshotCopy);
                return fetchedEntity;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error in retrieving " + entityType, e);
        }
    }

    public <E> void update(EntityKey key, E entity) {
        String updateSql = createUpdateSql(entity);
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(updateSql)) {
                final List<Object> entityFieldsValues = getEntityFieldsValues(entity);
                for (int i = 1; i <= entityFieldsValues.size(); i++) {
                    preparedStatement.setObject(i, entityFieldsValues.get(i - 1));
                }
                preparedStatement.setObject(3, key.getId());
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private <E> String createUpdateSql(E entity) {
        final String tableName = getTableName(entity.getClass());
        StringBuilder sb = new StringBuilder();
        sb.append("update ").append(tableName).append(" set ");
        Arrays.stream(entity.getClass().getDeclaredFields())
                .filter(f -> !"id".equals(f.getName()))
                .sorted(comparing(Field::getName))
                .forEach(f -> sb.append(getColumnName(f)).append("=?, "));
        sb.deleteCharAt(sb.lastIndexOf(", "));
        sb.append("where id=?");
        sb.append(";");
        return sb.toString();
    }

    @SneakyThrows
    private <T> T processResultSet(ResultSet resultSet, Class<T> entityType) {
        final T instance = entityType.getDeclaredConstructor().newInstance();
        resultSet.next();
        for (Field field : entityType.getDeclaredFields()) {
            field.setAccessible(true);
            field.set(instance, resultSet.getObject(getColumnName(field)));
        }
        return ((T) instance);
    }

    private <T> String getTableName(Class<T> entityType) {
        final Table columnAnnotation = entityType.getDeclaredAnnotation(Table.class);
        if (columnAnnotation != null) {
            return columnAnnotation.value();
        }
        return entityType.getSimpleName();
    }

    private String getColumnName(Field field) {
        final Column columnAnnotation = field.getDeclaredAnnotation(Column.class);
        if (columnAnnotation != null) {
            return columnAnnotation.value();
        }
        return field.getName();
    }

    private <E> void addSnapshotCopy(EntityKey<Class<E>, Object> key, E fetchedEntity, Map<EntityKey, List<Object>> snapshotCopy) {
        final List<Object> fieldValues = getEntityFieldsValues(fetchedEntity);
        snapshotCopy.put(key, fieldValues);
    }

    private <E> List<Object> getEntityFieldsValues(E fetchedEntity) {
        final List<Object> fieldValues = Arrays.stream(fetchedEntity.getClass().getDeclaredFields()).filter(f -> !"id".equals(f.getName())).sorted(comparing(Field::getName)).map(field -> {
            try {
                field.setAccessible(true);
                return field.get(fetchedEntity);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        return fieldValues;
    }

    @Override
    public void close() {
        cache.forEach((key, entity) -> {
            if (isDirty(key, entity)) {
                update(key, entity);
            }
        });
    }

    private boolean isDirty(EntityKey key, Object fetchedEntity) {
        final List<Object> copyFieldsValues = snapshotCopy.get(key);
        final List<Object> fetchedEntityFieldsValues = getEntityFieldsValues(fetchedEntity);
        for (int i = 0; i < fetchedEntityFieldsValues.size(); i++) {
            if (!copyFieldsValues.get(i).equals(fetchedEntityFieldsValues.get(i))) {
                return true;
            }
        }
        return false;
    }
}
