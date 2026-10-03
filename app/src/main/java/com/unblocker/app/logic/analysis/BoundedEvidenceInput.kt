package com.unblocker.app.logic.analysis

import java.io.Reader

/** Reads at most limit + 1 characters so corrupt lines cannot exhaust memory or CPU. */
internal object BoundedEvidenceInput {
    fun line(reader:Reader,limit:Int):String? {
        require(limit>0)
        val result=StringBuilder()
        while(true) {
            val c=reader.read()
            if(c<0) return if(result.isEmpty()) null else result.toString().trimEnd('\r')
            if(c==10) return result.toString().trimEnd('\r')
            if(result.length>=limit) throw IllegalArgumentException("Oversized evidence line")
            result.append(c.toChar())
        }
    }
}
