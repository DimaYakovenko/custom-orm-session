package orm;

import java.util.Objects;

public class EntityKey<E, ID> {
    private E type;
    private ID id;

    public EntityKey(E type, ID id) {
        this.type = type;
        this.id = id;
    }

    public E getType() {
        return type;
    }

    public void setType(E type) {
        this.type = type;
    }

    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityKey<?, ?> entityKey = (EntityKey<?, ?>) o;
        return Objects.equals(type, entityKey.type) && Objects.equals(id, entityKey.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }
}
