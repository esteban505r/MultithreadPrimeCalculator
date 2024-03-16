import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

@Composable
@Preview
fun App() {
    var calculateUntil by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(0.toLong()) }
    var primes by remember { mutableStateOf(listOf<Long>()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val coroutineCancellationHandler = CoroutineExceptionHandler { context, throwable ->
        loading = false
        print(throwable)
    }
    val job = Job()

    MaterialTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 25.dp).fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,modifier = Modifier.fillMaxWidth()) {
                Text("Duration: $duration milliseconds")
                Column {
                    Text("Prime Number Calculator")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Calculate until:")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(calculateUntil, onValueChange = {
                        calculateUntil = it
                    })
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            job.cancelChildren()
                            println("cancelling children")
                            job.children.forEach { it.join() }
                            primes = listOf()
                            println("Children cancelled")
                            // Calculating with one thread at a time
                            duration = measureTimeMillis {
                                calculatePrimes(2, calculateUntil.toLongOrNull() ?: 1) {
                                    //To avoid threads conflicts changing the list
                                    withContext(Dispatchers.Main) {
                                        println(Thread.currentThread().name)
                                        primes += it
                                    }
                                }
                            }
                        }
                    })
                    {
                        Text("Calculate with one thread")
                    }
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            job.cancelChildren()
                            println("cancelling children")
                            job.children.forEach { it.join() }
                            primes = listOf()
                            println("Children cancelled")
                            launch(job + Dispatchers.Default) {
                                duration = measureTimeMillis {
                                    separateCalculations(calculateUntil.toLongOrNull()?:1) {
                                        withContext(Dispatchers.Main) {
                                            primes += it
                                        }
                                    }
                                    primes = primes.sorted()
                                }
                            }
                        }

                    }) {
                        Text("Calculate with multithreading")
                    }
                }
            }

            if (loading) {
                CircularProgressIndicator()
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(50.dp),
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 30.dp)) {
                    items(primes.size) {
                        Text(primes[it].toString())
                    }
                }
            }


        }


    }
}



suspend fun separateCalculations(calculateUntil: Long,onPrime: suspend (Long) -> Unit) = coroutineScope{
    println("CORES:"+Runtime.getRuntime().availableProcessors())
    val tuple = calculateUntil / Runtime.getRuntime().availableProcessors().toLong()
    println("tuple:"+tuple)
    println("total:"+calculateUntil)

    val results = mutableListOf<Deferred<Unit>>()

    for (thread in 1..Runtime.getRuntime().availableProcessors().toLong()){
        results += async{
            calculatePrimes(tuple*(thread-1),tuple*thread){
                onPrime(it)
            }
        }
    }

    results.forEach{it.await()}

}

suspend fun calculatePrimes(calculateFrom:Long,calculateUntil: Long, onPrime: suspend (Long) -> Unit){
    val calculateFromReal = if(calculateFrom<2) 2 else calculateFrom
    println("calculating from $calculateFrom to $calculateUntil")
    for (number in calculateFromReal..(calculateUntil)) {
        val isPrime = calculatePrime(number)
        if (isPrime) {
            onPrime(number)
        }
    }
}

suspend fun calculatePrime(number: Long): Boolean {
    //println("calculating $number")
    for (temp in 2..<number) {
        yield()
        if (number % temp == 0.toLong()) {
            return false
        }
    }
    return true
}

fun main() = application {
    Window(onCloseRequest = {
        exitApplication()
    }) {
        App()
    }
}
