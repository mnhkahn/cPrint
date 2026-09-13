cmake_minimum_required(VERSION 3.22.1)
project("cprint")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Add native library
add_library(
    cprint-native
    SHARED
    native-lib.cpp
    escpr_writer.c
    escp2_writer.c
)

# Link libraries
target_link_libraries(
    cprint-native
    ${log-lib}
    ${android-lib}
)

# Include directories
target_include_directories(cprint-native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
