

## 扩展点

* 1. 根据项目自动在info里面注入title和version(使用pom的版本)
* 2. 优化如果没有设置RequestMethod, 会自动识别并增加配置项defaultRequestMethod来做默认method
* 3. 优化参数没有注解也可被识别进swagger


## 使用

```
 <plugin>
    <groupId>com.github.kongchen</groupId>
    <artifactId>swagger-maven-plugin</artifactId>
    <version>版本号</version>
    <configuration>
        <apiSources>
            <apiSource>
                <locations>扫描的包名</locations>
                <defaultRequestMethod>默认方法[GET | POST | ... ]</defaultRequestMethod>
                <typesToSkip>
                    <typeToSkip>
                        javax.servlet.http.HttpServletResponse
                    </typeToSkip>
                    <typeToSkip>
                        javax.servlet.http.HttpServletRequest
                    </typeToSkip>
                </typesToSkip>
            </apiSource>
        </apiSources>
    </configuration>
</plugin>
```
