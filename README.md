# deji

[![](https://jitpack.io/v/Mazar1ni/deji.svg)](https://jitpack.io/#Mazar1ni/deji)

A fast dependency injector for Kotlin and Android.
This dependency injector was created to inject dependencies more easily than Dagger or Hilt.

## Installation

For distribution, JitPack was chosen. So you need to add the JitPack repository to your build file and then add the dependency.

```
allprojects {
  repositories {
  ...
  maven { url 'https://jitpack.io' }
  }
}

...

dependencies {
  compileOnly 'com.github.Mazar1ni:deji:$version'
  kapt 'com.github.Mazar1ni:deji:$version'
}

```

Anyway you can just take jar file from Release and put to your project

```
compileOnly fileTree(dir: 'libs', include: ['*.jar'])
kapt fileTree(dir: 'libs', include: ['*.jar'])
```

## Documentation

There are four annotations:
* `Singleton`
* `Inject`
* `Setup`
* `SingletonRoom`

### Singleton

This annotation allows this class to be used everywhere as a single instance.

```
@Singleton
class UserInfo {
  val name = "Bill"
}
```

After build project, will generate the GeneratedSingleton file, which will contain the creation of the instance of this class.
Ерут you can use an instance of this class everywhere using the class name, but starting with a lowercase letter.

```
import com.example.project.GeneratedSingleton.userInfo

userInfo.name
```

### Setup

This annotation helps to provide classes that may need to have parameters entered or, for example, use a context as a singleton.
There will also be created instances of classes that you can use as a singleton.

```
@Setup
class AppSetup {
  fun providesApplicationContext(): Context = App.app
}
```

```
class App : Application() {
  companion object {
    lateinit var app: App
  }

  override fun onCreate() {
    app = this
  }
}
```

```
import com.example.project.GeneratedSingleton.context

context.packageName
```

### Inject

If you need to get an instance class in a module that is lower in the project hierarchy and you cannot get that class directly (e.g., a context or interface implementation), you can use this annotation.

Module B
```
interface IDataHandler {
  fun receivedData(value: Int)
}

class Sensor {
  @Inject
  lateinit var context: Context

  @Inject
  lateinit var dataHandler: IDataHandler
}
```

Module A which contains module B and implements the interface
```
@Singleton
class DataHandler: IDataHandler {
...
}
```

Also you need call init function in GeneratedSingleton class in module A for example in onCreate func in Application class
```
class App : Application() {
  override fun onCreate() {
    GeneratedSingleton.init()
  }
}
```

After building, you need to specify the GeneratedSingleton class in the @Setup class for the injections in module A.
```
@Setup([com.example.mooduleB.GeneratedSingleton.rootPackage])
class AppDISetup {
...
}
```

So, that's all you need. The only thing you need is to specify the variable names the same as the class name (ex: DataHandler class and dataHandler variable).

### SingletonRoom

This annotation will help for the dependency injector when using the [`Room library`].
It will create instances for each DAO class that is associated with the database class.

```
@Entity
data class User(
    @PrimaryKey val uid: Int,
    @ColumnInfo(name = "first_name") val firstName: String?,
    @ColumnInfo(name = "last_name") val lastName: String?
)

@Dao
interface UserDao {
    @Query("SELECT * FROM user")
    fun getAll(): List<User>
}

@Database(entities = [User::class], version = 1)
@SingletonRoom
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

```

Then you can use user DAO in your project

```
userDao.getAll()
```

## Enjoy
[`Room library`]: https://developer.android.com/training/data-storage/room
