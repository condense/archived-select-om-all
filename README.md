# select-om-all

Flexible select-style component for use in Om apps.

See it in action: http://www.condense.com.au/select-om-all/demo/

## Overview

This is a library written in clojurescript based using Om.  It can be used to provide
select-style inputs for HTML forms.

## Rationale

HTML Select fields are quite limited and most webapps projects demand something
more than they provide to help the user or improve performance.

We were driven by the need for some specific features:

* Handling thousands of select options efficiently
* Auto complete style filtering of select options
* Showing results in a grid format

Plenty of JS libraries have been created to try and address this, for example:

* Harvest Chosen was an innovation in its day
* Select2 is more modern popular solution
* Commercial libraries such as DevExpress have SearchBox and EditBox style inputs  
* Google Closure itself provides auto complete features

Attempts have been made to wrap these neatly into React components.  This library
aims to provide an Om native solution to your better-than-select input needs.

More recently Facebook released fixed-data-table which provides an efficient 
component for large tables of data.  We use this in our solution.

## Documentation

Please see this [Google Doc](https://docs.google.com/a/condense.com.au/document/d/1LfdY2gxhAQWOBoB3YDIi10grwYYGEqeHOZDwEccdrbg/edit#) for the current API documentation.  Feel free to comment if
you have a suggestion to improve the documentation but use GitHub repo issues for
feature requests and bugs.

## Setup

Builds can use:

    lein cljsbuild auto

Clean project specific out:

     lein clean

For more info, read [Waitin'](http://swannodette.github.io/2014/12/22/waitin/).

## License

Copyright © 2015 Condense Pty Ltd

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
