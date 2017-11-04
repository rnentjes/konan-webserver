cmake_minimum_required(VERSION 3.8)

set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_LIST_DIR}/KotlinCMakeModule)

project(webserver Kotlin)


set(BUFFERS_SRC "src/common" "src/buffers")

konanc_executable(
        NAME TestBuffers
        SOURCES ${BUFFERS_SRC}
)

set(WEBSERVER_SRC "src/common" "src/server")
konanc_executable(
        NAME WebServer
        SOURCES ${WEBSERVER_SRC}
)

set(ASYNC_SRC "src/common" "src/async")
konanc_executable(
        NAME AsyncServer
        SOURCES ${ASYNC_SRC}
)
