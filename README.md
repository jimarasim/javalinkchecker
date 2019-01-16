1. Install Java
2. Set JAVA_HOME in ~/.bash_profile
    export JAVA_HOME=$(/usr/libexec/java_home)
3. Download and install Maven
    https://www.mkyong.com/maven/install-maven-on-mac-osx/
4. cd to the project directory (where pom.xml is located).
5. Run the test
    mvn -Dtest=JavaLinkChecker test
6. Review the report
    linkcrawler.htm