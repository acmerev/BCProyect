package com.bc;

import org.springframework.jdbc.core.JdbcTemplate; // Importamos JdbcTemplate para ejecutar consultas SQL
import javax.sql.DataSource; // Importamos DataSource para obtener la conexión a la base de datos
import org.slf4j.Logger; // Importamos Logger para realizar registros
import org.slf4j.LoggerFactory; // Importamos LoggerFactory para obtener un Logger
import org.springframework.batch.core.BatchStatus; // Importamos BatchStatus para verificar el estado del lote
import org.springframework.batch.core.Job; // Importamos Job para definir un trabajo
import org.springframework.batch.core.JobExecution; // Importamos JobExecution para obtener información sobre la ejecución del trabajo
import org.springframework.batch.core.Step; // Importamos Step para definir pasos en un trabajo
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer; // Importamos DefaultBatchConfigurer para configurar el lote por defecto
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing; // Importamos EnableBatchProcessing para habilitar el procesamiento de lotes
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory; // Importamos JobBuilderFactory para crear objetos Job
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory; // Importamos StepBuilderFactory para crear objetos Step
import org.springframework.batch.core.launch.support.RunIdIncrementer; // Importamos RunIdIncrementer para incrementar el ID de ejecución del trabajo
import org.springframework.batch.core.listener.JobExecutionListenerSupport; // Importamos JobExecutionListenerSupport para escuchar eventos de ejecución del trabajo
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider; // Importamos BeanPropertyItemSqlParameterSourceProvider para proporcionar parámetros de SQL
import org.springframework.batch.item.database.JdbcBatchItemWriter; // Importamos JdbcBatchItemWriter para escribir en la base de datos mediante lotes
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder; // Importamos JdbcBatchItemWriterBuilder para construir un escritor de lotes JDBC
import org.springframework.batch.item.file.FlatFileItemReader; // Importamos FlatFileItemReader para leer datos de un archivo plano
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder; // Importamos FlatFileItemReaderBuilder para construir un lector de archivos planos
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper; // Importamos BeanWrapperFieldSetMapper para mapear los campos del archivo a un objeto Java
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization; // Importamos DependsOnDatabaseInitialization para depender de la inicialización de la base de datos
import org.springframework.context.annotation.Bean; // Importamos Bean para definir beans de Spring
import org.springframework.context.annotation.Configuration; // Importamos Configuration para marcar la clase como una clase de configuración de Spring
import org.springframework.core.io.ClassPathResource; // Importamos ClassPathResource para acceder a recursos en el classpath
import org.springframework.jdbc.datasource.DataSourceTransactionManager; // Importamos DataSourceTransactionManager para administrar transacciones de datos
import org.springframework.transaction.PlatformTransactionManager; // Importamos PlatformTransactionManager para administrar transacciones

import com.bc.model.Usuario; // Importamos la clase Usuario para representar los datos del usuario

@Configuration
@EnableBatchProcessing
public class BatchConfiguration extends DefaultBatchConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class); // Definimos un Logger

    private final JobBuilderFactory jobBuilderFactory; // Inyectamos JobBuilderFactory para construir objetos Job
    private final StepBuilderFactory stepBuilderFactory; // Inyectamos StepBuilderFactory para construir objetos Step
    private final DataSource dataSource; // Inyectamos DataSource para acceder a la base de datos
    private final JdbcTemplate jdbcTemplate; // Inyectamos JdbcTemplate para ejecutar consultas SQL

    public BatchConfiguration(JobBuilderFactory jobBuilderFactory, StepBuilderFactory stepBuilderFactory, DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PlatformTransactionManager getTransactionManager() {
        return new DataSourceTransactionManager(dataSource); // Configuramos el administrador de transacciones
    }

    @Bean
    public FlatFileItemReader<Usuario> reader() {
        return new FlatFileItemReaderBuilder<Usuario>() // Creamos un lector de archivos planos para leer datos del archivo CSV
                .name("usuarioReader")
                .resource(new ClassPathResource("usuarios.csv")) // Especificamos la ubicación del archivo CSV
                .linesToSkip(1) // Omitimos la primera línea que contiene encabezados
                .delimited() // Especificamos que los datos están separados por delimitadores
                .names(new String[]{"nombre", "apellido", "edad", "email"}) // Especificamos los nombres de las columnas
                .fieldSetMapper(new BeanWrapperFieldSetMapper<Usuario>() {{ // Mapeamos los campos del archivo a un objeto Usuario
                    setTargetType(Usuario.class);
                }})
                .build();
    }

    @Bean
    @DependsOnDatabaseInitialization
    public JdbcBatchItemWriter<Usuario> writer() {
        return new JdbcBatchItemWriterBuilder<Usuario>() // Creamos un escritor de lotes JDBC para escribir en la base de datos
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>()) // Proporcionamos parámetros de SQL utilizando los nombres de las propiedades del objeto Usuario
                .sql("INSERT INTO usuario (nombre, apellido, edad, email) VALUES (:nombre, :apellido, :edad, :email)") // Especificamos la consulta SQL para insertar datos en la tabla usuario
                .dataSource(dataSource) // Configuramos el origen de datos
                .build();
    }

    @Bean
    public Step step1(JdbcBatchItemWriter<Usuario> writer) {
        return stepBuilderFactory.get("step1") // Creamos un paso en el trabajo
                .<Usuario, Usuario>chunk(10) // Especificamos el tamaño del fragmento
                .reader(reader()) // Configuramos el lector
                .writer(writer) // Configuramos el escritor
                .build();
    }

    @Bean
    public Job importUserJob(Step step1) {
        return jobBuilderFactory.get("importUserJob") // Creamos un trabajo para importar usuarios
                .incrementer(new RunIdIncrementer()) // Incrementamos el ID de ejecución del trabajo
                .flow(step1) // Definimos el flujo del trabajo
                .end() // Definimos el final del flujo
                .listener(new JobCompletionNotificationListener(jdbcTemplate)) // Agregamos un oyente para el trabajo
                .build();
    }

    public static class JobCompletionNotificationListener extends JobExecutionListenerSupport {

        private final JdbcTemplate jdbcTemplate; // Inyectamos JdbcTemplate para ejecutar consultas SQL

        public JobCompletionNotificationListener(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public void afterJob(JobExecution jobExecution) {
            if (jobExecution.getStatus() == BatchStatus.COMPLETED) { // Verificamos si el trabajo se completó con éxito
                logger.info("Job completed successfully!"); // Registramos un mensaje de éxito en el registro

                logger.info("Registros insertados en la base de datos:"); // Registramos un mensaje en el registro

                jdbcTemplate.query("SELECT id, nombre, apellido, edad, email FROM usuario", // Ejecutamos una consulta SQL para obtener los registros insertados en la base de datos
                        (rs, row) -> new Usuario( // Mapeamos los resultados de la consulta a objetos Usuario
                                rs.getString("nombre"),
                                rs.getString("apellido"),
                                rs.getInt("edad"),
                                rs.getString("email"))
                ).forEach(usuario -> logger.info(usuario.toString())); // Registramos cada usuario en el registro
            }
        }
    }
}

