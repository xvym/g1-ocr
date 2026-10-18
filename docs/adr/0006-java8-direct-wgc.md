# 使用 Java 8、JavaFX 8 与 JNA 直接调用系统 WGC

用户明确选择 Java 8 与 JavaFX 8，并希望免去项目自有采集 DLL 的维护，因此采用 JNA 映射系统 WinRT、WGC 与 D3D11 接口，Python 只用于开发和建库。接口依据支持这条路线，但实际 Java 绑定、ABI、线程与资源释放必须先用最小原型验证；不因实现复杂就默认改为 C# 或自编译 C++ DLL。JNA 和 JavaFX 的标准原生组件仍随包提供，免维护自有 DLL 不代表没有第三方原生依赖。
