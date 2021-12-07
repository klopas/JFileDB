package com.fgc.projects.repositories;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import com.fgc.projects.models.Entity;
import com.fgc.projects.models.Id;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public abstract class FileRepository<T, I> {

  private static final String BASE_DIRECTORY_NAME = "file-database";

  private static final String HOME = System.getProperty("user.home");

  private final Logger logger;

  private final Class<T> clazz;

  protected FileRepository(final Logger logger) {
    this.logger = logger;
    this.clazz = (Class<T>) ((ParameterizedType)getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    this.createFileIfNotExist();
  }

  private void createFileIfNotExist() {
    try {
      Files.createDirectories(this.getDirectoryPath());
    } catch (IOException e) {
      logger.warn(e.getMessage());
    }

    try {
      if (Files.notExists(this.getPath())) {
        Files.write(this.getPath(), "[]".getBytes(), StandardOpenOption.CREATE);
      }
    } catch (IOException e) {
      logger.warn(e.getMessage());
    }
  }

  public void save(final T entity) {
    try {
      Preconditions.checkArgument(entity != null);
      final String id = (String) this.getIdValue(entity);
      Preconditions.checkArgument(StringUtils.isNotEmpty(id));
    } catch (IllegalArgumentException e) {
      this.logger.error("Null entity or Null Id");
      return;
    }

    this.saveEntity(entity);
  }

  private void saveEntity(T e) {
    final List<T> all = this.findAll();
    final int index = all.indexOf(e);

    if (index != -1) {
      all.remove(index);
      all.add(index, e);
    } else {
      all.add(e);
    }

    this.saveList(all);
  }

  public void saveAll(final List<T> all) {
    Preconditions.checkArgument(CollectionUtils.isNotEmpty(all));

    final List<T> allSaved = this.findAll();
    allSaved.addAll(all);
    this.saveList(allSaved);
  }

  private void saveList(final List<T> all) {
    try {
      Files.write(this.getPath(), new Gson().toJson(all).getBytes(),
        StandardOpenOption.WRITE, StandardOpenOption.SYNC);
    } catch (IOException e) {
      this.logger.error("Could not update file with all");
    }
  }

  private Path getDirectoryPath() {
    return Paths.get(HOME,BASE_DIRECTORY_NAME);
  }

  private Path getPath() {
    final String fileName = this.clazz.getAnnotation(Entity.class).value();
    return Paths.get(this.getDirectoryPath().toString(), fileName + ".json");
  }

  public List<T> findAll() {
    try {
      return this.getList(Files.readString(this.getPath()));
    } catch (IOException e) {
      this.logger.error("Could not Find All from database.");
      return List.of();
    }
  }

  private List<T> getList(String value) {
    final Type typeOfT = TypeToken.getParameterized(List.class, this.clazz).getType();
    return new Gson().fromJson(value, typeOfT);
  }

  public Optional<T> findById(I id) {
    Preconditions.checkNotNull(id);

    return this.findAll().stream().filter(entity -> id.equals(this.getIdValue(entity))).findAny();
  }

  public void deleteById(I id) {
    Preconditions.checkNotNull(id);

    final List<T> all = this.findAll();
    findInListById(id, all).ifPresent(all::remove);
    this.saveList(all);
  }

  private Optional<T> findInListById(I id, List<T> all) {
    return all.stream().filter(e -> this.getIdValue(e).equals(id)).findFirst();
  }

  private Object getIdValue(final T o) {
    final List<Field> fields = Arrays.stream(o.getClass().getDeclaredFields()).collect(Collectors.toList());

    final Optional<Field> idField = fields.stream().filter(field -> {
      final List<Annotation> declaredAnnotations = Arrays.stream(field.getDeclaredAnnotations()).collect(Collectors.toList());
      return declaredAnnotations.stream().anyMatch(a -> Id.class.equals(a.annotationType()));
    }).findFirst();

    final Optional<Method> methodName = idField.map(
      f -> "get" + f.getName().toUpperCase().charAt(0) + f.getName().substring(1)).map(name -> {
      try {
        return o.getClass().getDeclaredMethod(name);
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
        return null;
      }
    });

    Object invoke = null;
    try {
    final Method declaredMethod = o.getClass().getDeclaredMethod(methodName.get().getName());
      invoke = declaredMethod.invoke(o);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      e.printStackTrace();
    }

    return invoke;
  }

}
