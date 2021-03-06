package com.github.wrdlbrnft.simpleorm.processor;

import com.github.wrdlbrnft.codebuilder.code.SourceFile;
import com.github.wrdlbrnft.codebuilder.util.Utils;
import com.github.wrdlbrnft.simpleorm.annotations.Database;
import com.github.wrdlbrnft.simpleorm.processor.analyzer.databases.DatabaseAnalyzer;
import com.github.wrdlbrnft.simpleorm.processor.analyzer.databases.DatabaseInfo;
import com.github.wrdlbrnft.simpleorm.processor.analyzer.entity.EntityInfo;
import com.github.wrdlbrnft.simpleorm.processor.analyzer.typeadapter.TypeAdapterAnalyzer;
import com.github.wrdlbrnft.simpleorm.processor.analyzer.typeadapter.TypeAdapterManager;
import com.github.wrdlbrnft.simpleorm.processor.builder.databases.factory.DatabaseFactoryBuilder;
import com.github.wrdlbrnft.simpleorm.processor.builder.entitybuilder.EntityBuilderBuilder;
import com.github.wrdlbrnft.simpleorm.processor.builder.f.FieldConstantsClassBuilder;
import com.github.wrdlbrnft.simpleorm.processor.builder.f.FieldInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Created with Android Studio
 * User: Xaver
 * Date: 02/07/16
 */
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class SimpleOrmProcessor extends AbstractProcessor {

    private TypeAdapterAnalyzer mTypeAdapterAnalyzer;
    private DatabaseAnalyzer mDatabaseAnalyzer;
    private FieldConstantsClassBuilder mFieldConstantsClassBuilder;
    private DatabaseFactoryBuilder mDatabaseFactoryBuilder;
    private EntityBuilderBuilder mBuilderBuilder;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mTypeAdapterAnalyzer = new TypeAdapterAnalyzer(processingEnv);
        mDatabaseAnalyzer = new DatabaseAnalyzer(processingEnv);
        mFieldConstantsClassBuilder = new FieldConstantsClassBuilder(processingEnv);
        mDatabaseFactoryBuilder = new DatabaseFactoryBuilder(processingEnv);
        mBuilderBuilder = new EntityBuilderBuilder(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        final TypeAdapterManager adapterManager = mTypeAdapterAnalyzer.analyze(roundEnv);
        try {
            final List<TypeElement> databases = getDatabases(roundEnv);
            final List<DatabaseInfo> databaseInfos = mDatabaseAnalyzer.analyze(databases, adapterManager);
            final Set<EntityInfo> entityInfos = getAllEntityInfos(databaseInfos);
            mFieldConstantsClassBuilder.build(entityInfos);
            for (DatabaseInfo databaseInfo : databaseInfos) {
                final String packageName = Utils.getPackageName(databaseInfo.getTypeElement());
                final SourceFile databaseSourceFile = SourceFile.create(processingEnv, packageName);
                databaseSourceFile.write(mDatabaseFactoryBuilder.build(databaseInfo));
                databaseSourceFile.flushAndClose();
            }

            for (EntityInfo entityInfo : entityInfos) {
                final String packageName = Utils.getPackageName(entityInfo.getEntityElement());
                final SourceFile builderSourceFile = SourceFile.create(processingEnv, packageName);
                builderSourceFile.write(mBuilderBuilder.build(entityInfo));
                builderSourceFile.flushAndClose();
            }

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE,
                    "Failed to process SimpleORM Entities. This may be an internal error in SimpleORM. Check the stacktrace in the compiler output for more information."
            );
            e.printStackTrace();
        }

        return false;
    }

    private Set<EntityInfo> getAllEntityInfos(List<DatabaseInfo> databaseInfos) {
        final Set<EntityInfo> entityInfos = new HashSet<>();
        for (DatabaseInfo info : databaseInfos) {
            entityInfos.addAll(info.getEntityInfos());
        }
        return entityInfos;
    }

    private ArrayList<TypeElement> getDatabases(RoundEnvironment roundEnv) {
        final ArrayList<TypeElement> entities = new ArrayList<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(Database.class)) {
            entities.add((TypeElement) element);
        }
        return entities;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        final Set<String> types = new HashSet<>();
        types.add(Database.class.getCanonicalName());
        return types;
    }
}
