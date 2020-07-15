## 原作者
```
https://github.com/kongchen/swagger-maven-plugin
```

## 扩展点

* 1. 不再强制要求swagger, 自动使用SpringMVC模式进行扫描
* 2. 根据项目自动在info里面注入title和version(使用pom的版本)
* 3. 优化如果没有设置RequestMethod, 会自动识别并增加配置项defaultRequestMethod来做默认method
* 4. 优化参数没有注解也可被识别进swagger
* 5. 构建完成后会把swagger.json上传到指定接口(post + json)
* 6. 修复ApiImplicitParam的dataType找不到的话, 不使用controller
* 7. 支持javax 和 hibernate 的验证抽取, 会在Defined里面加个x-rules, 规则是form-validator
* 8. 支持使用javadoc来用于注释(正则抽取, 使用JavaParser分析)
* 9. 增加versionEnv配置, 用于读取当前构建的版本号
* 10. 支持dubbo接口凑趣

## 验证规则抽取

(https://github.com/yiminghe/async-validator/blob/master/README.md)[https://github.com/yiminghe/async-validator/blob/master/README.md]
目前支持 NotBlank, Pattern, NotNull, Size, Range, Length

```
"x-rules": {
    "字段名": [
        验证规则
    ]
}
```

## 注释规则抽取

```java
/**
 * 字段注释
 */

/**
 * 方法注释
 * @param
 * @result
 */
```


## 使用

```
 <plugin>
    <groupId>com.github.kongchen</groupId>
    <artifactId>swagger-maven-plugin</artifactId>
    <version>版本号</version>
    <configuration>
        <verionEnv>构建环境变量,例如:MVN_RELEASE_VERSION</versionEnv>
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
                <callbacks>
                    <callback>http://</callback>
                </callbacks>
            </apiSource>
            <apiSource>
                <locations></location>
                <dubbo>true</dubbo>
            </apiSource>
        </apiSources>
    </configuration>
</plugin>
```


## 相关项目

### 接受通知构建ts,上传npm私服

https://github.com/binlaniua/swagger-maven-callback
