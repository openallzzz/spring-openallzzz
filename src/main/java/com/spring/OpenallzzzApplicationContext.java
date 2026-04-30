package com.spring;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class OpenallzzzApplicationContext { // 容器类

    private final Class<?> configClass;

    private ConcurrentHashMap<String, Object> singletonObjects = new ConcurrentHashMap<>(); // 单例bean池
    private ConcurrentHashMap<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(); // bean定义池
    private List<BeanPostProcessor> beanPostProcessorList = new ArrayList<>();

    public OpenallzzzApplicationContext(Class<?> configClass) {
        this.configClass = configClass;

        // 解析配置类
        // ComponentScan注解 --> 扫描路径 -- > 扫描 --> BeanDefinition --> BeanDefinitionMap
        scan(configClass);

        for (String beanName : beanDefinitionMap.keySet()) {
            BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
            if (beanDefinition.getScope().equals("singleton")) {
                Object bean = createBean(beanName, beanDefinition);
                singletonObjects.put(beanName, bean); // 初始化单例池
            }
        }
    }

    public Object createBean(String beanName, BeanDefinition beanDefinition) {
        // 1. 调用BeanDefinition获取bean的定义信息
        System.out.println("1. 调用BeanDefinition获取bean的定义信息");
        Class<?> clazz = beanDefinition.getClazz();
        try {
            // 2. 构造函数
            System.out.println("2. 构造函数");
            Object instance = clazz.getDeclaredConstructor().newInstance();

            // 3. 依赖注入（DI） -- 创建bean的时候才需要进行的操作
            System.out.println("3. 依赖注入（DI） -- 创建bean的时候才需要进行的操作");
            Field[] declaredFields = clazz.getDeclaredFields();
            for (Field field : declaredFields) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    Object bean = getBean(field.getName()); // byName实现依赖注入
                    field.setAccessible(true); // 私有属性设置可访问的
                    field.set(instance, bean);
                }
            }

            // 4. Aware回调 -- 设置bean的名称
            System.out.println("4. Aware回调 -- 设置bean的名称");
            if (instance instanceof BeanNameAware) {
                ((BeanNameAware) instance).setBeanName(beanName);
            }

            // 5. postProcessBeforeInitialization
            System.out.println("5. postProcessBeforeInitialization");
            for (BeanPostProcessor beanPostProcessor : beanPostProcessorList) {
                instance = beanPostProcessor.postProcessBeforeInitialization(instance, beanName);
            }

            // 6. 初始化
            System.out.println("6. 初始化 --> " + clazz);
            if (instance instanceof InitializingBean) {
                try {
                    ((InitializingBean) instance).afterPropertiesSet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 7. postProcessAfterInitialization
            System.out.println("7. postProcessAfterInitialization");
            for (BeanPostProcessor beanPostProcessor : beanPostProcessorList) {
                instance = beanPostProcessor.postProcessAfterInitialization(instance, beanName);
            }

            return instance;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public Object getBean(String beanName) {
        // 根据类名获取类，再获取类上的scope注解
        if (beanDefinitionMap.containsKey(beanName)) {
            BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
            if (beanDefinition.getScope().equals("singleton")) {
                return singletonObjects.get(beanName);
            } else {
                // 创建bean对象（原型bean）
                return createBean(beanName, beanDefinition);
            }
        } else {
            // 不存在对应的bean
            throw new NullPointerException();
        }
    }

    private void scan(Class<?> configClass) {
        ComponentScan componentScanAnnotation = configClass.getDeclaredAnnotation(ComponentScan.class);
        String path = componentScanAnnotation.value(); // 获取注解上的类路径

        path = path.replace('.', '/'); // 替换成文件分割符

        // 扫描
        /*类加载器
            Bootstrap-->jre/lib
            Ext-->jre/ext/lib
            App-->classpath
        */

        // 获取类加载器
        ClassLoader classLoader = OpenallzzzApplicationContext.class.getClassLoader();
        URL resource = classLoader.getResource(path);
        File file = new File(resource.getFile());
        if (file.isDirectory()) {
            File[] files = file.listFiles(); // 获取某个包下的所有文件
            for (File f : files) {
                String fileName = f.getAbsolutePath();
                if (fileName.endsWith(".class")) { // 判断是否是类文件
                    String className = fileName.substring(fileName.indexOf("com"), fileName.indexOf(".class"));
                    className = className.replace('/', '.').replace('\\', '.');
                    Class<?> aClass = null;
                    try {
                        aClass = classLoader.loadClass(className);
                        if (aClass.isAnnotationPresent(Component.class)) { // 当前类是一个bean
                            // 解析类，判断当前bean是单例bean，还是prototype的bean
                            // 生成 --> BeanDefinition

                            if (BeanPostProcessor.class.isAssignableFrom(aClass)) {
                                BeanPostProcessor instance = (BeanPostProcessor) aClass
                                        .getDeclaredConstructor().newInstance();
                                beanPostProcessorList.add(instance);
                            }

                            Component componentAnnotation = aClass.getDeclaredAnnotation(Component.class);
                            String beanName = componentAnnotation.value();

                            // 定义bean的标识类（重要）
                            BeanDefinition beanDefinition = new BeanDefinition();
                            beanDefinition.setClazz(aClass);
                            if (aClass.isAnnotationPresent(Scope.class)) {
                                Scope scopeAnnotation = aClass.getDeclaredAnnotation(Scope.class);
                                beanDefinition.setScope(scopeAnnotation.value());
                            } else {
                                beanDefinition.setScope("singleton"); // 默认为单例bean
                            }

                            // 最终目的
                            beanDefinitionMap.put(beanName, beanDefinition);
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    } catch (InstantiationException e) {
                        throw new RuntimeException(e);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

}
