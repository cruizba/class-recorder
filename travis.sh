#!/bin/sh
mkdir class-recorder
git clone https://github.com/Class-Recorder/docker-class-recorder
cd docker-class-recorder/docker-runnables/crecorder-ci
./docker_run
