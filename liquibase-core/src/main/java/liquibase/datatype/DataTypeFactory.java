package liquibase.datatype;

import liquibase.database.Database;
import liquibase.database.structure.DataType;
import liquibase.datatype.core.UnknownType;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.servicelocator.ServiceLocator;
import liquibase.util.ObjectUtil;
import liquibase.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataTypeFactory {

    private static DataTypeFactory instance;

    private Map<String, SortedSet<Class<? extends LiquibaseDataType>>> registry = new ConcurrentHashMap<String, SortedSet<Class<? extends LiquibaseDataType>>>();

    private DataTypeFactory() {
        Class<? extends LiquibaseDataType>[] classes;
        try {
            classes = ServiceLocator.getInstance().findClasses(LiquibaseDataType.class);

            for (Class<? extends LiquibaseDataType> clazz : classes) {
                //noinspection unchecked
                register(clazz);
            }

        } catch (Exception e) {
            throw new UnexpectedLiquibaseException(e);
        }

    }

    public static synchronized DataTypeFactory getInstance() {
        if (instance == null) {
            instance = new DataTypeFactory();
        }
        return instance;
    }

    public static void reset() {
        instance = new DataTypeFactory();
    }


    public void register(Class<? extends LiquibaseDataType> dataTypeClass) {
        try {
            LiquibaseDataType example = dataTypeClass.newInstance();
            List<String> names = new ArrayList<String>();
            names.add(example.getName());
            names.addAll(Arrays.asList(example.getAliases()));

            for (String name : names) {
                name = name.toLowerCase();
                if (registry.get(name) == null) {
                    registry.put(name, new TreeSet<Class<? extends LiquibaseDataType>>(new Comparator<Class<? extends LiquibaseDataType>>() {
                        public int compare(Class<? extends LiquibaseDataType> o1, Class<? extends LiquibaseDataType> o2) {
                            try {
                                return -1 * new Integer(o1.newInstance().getPriority()).compareTo(o2.newInstance().getPriority());
                            } catch (Exception e) {
                                throw new UnexpectedLiquibaseException(e);
                            }
                        }
                    }));
                }
                registry.get(name).add(dataTypeClass);
            }
        } catch (Exception e) {
            throw new UnexpectedLiquibaseException(e);
        }
    }

    public void unregister(String name) {
        registry.remove(name.toLowerCase());
    }

    public Map<String, SortedSet<Class<? extends LiquibaseDataType>>> getRegistry() {
        return registry;
    }

    public LiquibaseDataType fromDescription(String dataTypeDefinition) {
        String dataTypeName = dataTypeDefinition;
        if (dataTypeName.matches(".*\\(.*")) {
            dataTypeName = dataTypeDefinition.replaceFirst("\\s*\\(.*", "");
        }
        if (dataTypeName.matches(".*\\{.*")) {
            dataTypeName = dataTypeDefinition.replaceFirst("\\s*\\{.*", "");
        }

        SortedSet<Class<? extends LiquibaseDataType>> classes = registry.get(dataTypeName.toLowerCase());

        LiquibaseDataType liquibaseDataType = null;
        if (classes == null) {
            liquibaseDataType = new UnknownType(dataTypeName);
        } else {

            try {
                liquibaseDataType = classes.iterator().next().newInstance();
            } catch (Exception e) {
                throw new UnexpectedLiquibaseException(e);
            }
        }
        if (liquibaseDataType == null) {
            liquibaseDataType = new UnknownType(dataTypeName);

        }

        if (dataTypeDefinition.matches("\\w+\\s*\\(.*")) {
            String paramStrings = dataTypeDefinition.replaceFirst(".*?\\(", "").replaceFirst("\\).*", "");
            String[] params = paramStrings.split(",");
            for (String param : params) {
                param = StringUtils.trimToNull(param);
                if (param != null) {
                    liquibaseDataType.addParameter(param);
                }
            }
        }

        if (dataTypeDefinition.matches(".*\\{.*")) {
            String paramStrings = dataTypeDefinition.replaceFirst(".*?\\{", "").replaceFirst("\\}.*", "");
            String[] params = paramStrings.split(",");
            for (String param : params) {
                param = StringUtils.trimToNull(param);
                if (param != null) {
                    String[] paramAndValue = param.split(":", 2);
                    try {
                        ObjectUtil.setProperty(liquibaseDataType, paramAndValue[0], paramAndValue[1]);
                    } catch (Exception e) {
                        throw new RuntimeException("Unknown property "+paramAndValue[0]+" for "+liquibaseDataType.getClass().getName());
                    }
                }
            }
        }

        return liquibaseDataType;

    }


    public LiquibaseDataType fromObject(Object object, Database database) {
        return fromDescription(object.getClass().getName());
    }

    public LiquibaseDataType from(DataType type) {
        return null; //todo
    }

    public LiquibaseDataType from(DatabaseDataType type) {
        return null; //todo
    }

    public String getTrueBooleanValue(Database database) {
        return fromDescription("boolean").objectToString(true, database);
    }

    public String getFalseBooleanValue(Database database) {
        return fromDescription("boolean").objectToString(false, database);
    }
}
