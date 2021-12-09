FROM ubuntu:20.04

LABEL maintainer="Johannes Bechberger"
LABEL version="1.0"
LABEL Description="Nildumu evaluation setup"

ENV DEBIAN_FRONTEND=noninteractive

# RUN add-apt-repository -y ppa:ubuntu-toolchain-r/test
RUN apt-get update
RUN apt-get install --no-install-recommends -y libboost-program-options-dev python gcc git g++ make cmake \
 zlib1g-dev wget make libgmp-dev unzip maven libc6-dev gcc-multilib g++-multilib vim emacs nano \
 curl ninja-build flex bison libxml2-utils patch

# Install OpenJDK-8     # see: https://stackoverflow.com/a/44058196
RUN apt-get update && \
    apt-get install -y openjdk-8-jdk && \
    apt-get install -y ant && \
    apt-get clean;

# Fix certificate issues
RUN apt-get update && \
    apt-get install ca-certificates-java && \
    apt-get clean && \
    update-ca-certificates -f;

# Setup JAVA_HOME -- useful for docker commandline
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/
RUN export JAVA_HOME

# build M4RI for ApproxFlow
WORKDIR /
RUN wget https://bitbucket.org/malb/m4ri/downloads/m4ri-20200125.tar.gz
RUN tar -xvf m4ri-20200125.tar.gz
WORKDIR m4ri-20200125
RUN ./configure
RUN make && make install

# build CryptoMiniSat for ApproxFlow
WORKDIR /
RUN wget https://github.com/msoos/cryptominisat/archive/5.8.0.tar.gz
RUN tar -xvf 5.8.0.tar.gz
WORKDIR /cryptominisat-5.8.0
RUN mkdir build
WORKDIR /cryptominisat-5.8.0/build
RUN cmake -DSTATICCOMPILE=ON ..
RUN make -j6 && make install

# build ApproxMC for ApproxFlow
RUN git clone https://github.com/meelgroup/approxmc /approxmc
WORKDIR /approxmc
RUN git checkout c9144b7b0f1c13f5c2f2d507e9493093b1afd4ff
RUN mkdir build
WORKDIR /approxmc/build
RUN cmake -DSTATICCOMPILE=ON ..
RUN make -j6 && make install

# install CBMC for ApproxFlow
RUN wget https://github.com/diffblue/cbmc/releases/download/cbmc-5.21.0/ubuntu-20.04-cbmc-5.21.0-Linux.deb
RUN dpkg -i ubuntu-20.04-cbmc-5.21.0-Linux.deb

# install pycparser for ApproxFlow
RUN curl https://bootstrap.pypa.io/pip/2.7/get-pip.py --output get-pip.py
RUN python2 get-pip.py
RUN pip install pycparser

# clone nildumu
RUN git clone https://github.com/parttimenerd/nildumu  /nildumu --recursive
WORKDIR /nildumu
RUN git submodule update --recursive --remote
RUN git pull
RUN cp /approxmc/build/approxmc /nildumu/eval-programs/approxflow/util/scalmc

RUN ./download_solvers
RUN mvn compile -DskipTests=true > /dev/null
