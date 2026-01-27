#!/usr/bin/env tclsh

# Получение списка JMX-файлов
proc getJmxFiles {} {
    set files [glob -nocomplain *.jmx]
    if {[llength $files] == 0} {
        puts "Ошибка: нет JMX-файлов в текущей директории."
        exit 1
    }
    return [lsort $files]
}

# Отображение меню
proc showMenu {files} {
    puts "\n=== Выберите тест JMeter ==="
    for {set i 0} {$i < [llength $files]} {incr i} {
        set test_name [file rootname [lindex $files $i]]
        puts "[expr {$i + 1}]) $test_name"
    }
    puts "0) Выход"
    puts -nonewline "Введите номер теста (1-[llength $files]) или 0: "
    flush stdout
}

# Запуск JMeter в фоне и мониторинг
proc runTestBackground {filename} {
    set test_name [file rootname $filename]
    set timestamp [clock format [clock seconds] -format {%Y-%m-%d-%H-%M}]
    set result_dir "result/$test_name/$timestamp"
    set csv_file "$result_dir/results.csv"
    set log_file "$result_dir/log.log"
    set report_dir "$result_dir/report"

    file mkdir $result_dir

    # Формируем команду
    set cmd [list \
        bin/jmeter \
        -n \
        -t $filename \
        -l $csv_file \
        -j $log_file \
        -e \
        -o $report_dir \
    ]

    puts "Запуск теста: $test_name"
    puts "Результаты:"
    puts "  CSV:  $csv_file"
    puts "  Log:  $log_file"
    puts "  Отчет: $report_dir"
    puts "Ожидание завершения..."

    # Запускаем в фоне и получаем PID
    if {[catch {exec {*}$cmd &} pid]} {
        puts "Ошибка при запуске: $pid"
        return
    }

    puts "PID процесса: $pid"
    puts "Статус: Running..."

    # Мониторинг процесса по PID
    while {1} {
        # Проверяем, существует ли процесс с таким PID
        if {![isProcessRunning $pid]} {
            break
        }
        # Можно добавить задержку, чтобы не грузить CPU
        after 2000  ;# 2 секунды
        puts -nonewline "."
        flush stdout
    }

    # После завершения
    if {[catch {exec kill -0 $pid} err]} {
        # Процесс завершён
        puts "\nТест завершён. PID $pid больше не активен."
    } else {
        puts "\nПредупреждение: процесс $pid всё ещё существует (возможно, завис)."
    }

    puts "Проверка результатов в: $result_dir"
}

# Проверка, жив ли процесс с данным PID
proc isProcessRunning {pid} {
    if {$pid <= 0} { return 0 }
    if {[catch {exec kill -0 $pid}]} {
        return 0  ;# Процесс не существует или недоступен
    }
    return 1  ;# Процесс жив
}

# Основная логика
set files [getJmxFiles]
set count [llength $files]

# Если передан аргумент
if {$argc > 0} {
    set arg [lindex $argv 0]
    if {![string is integer -strict $arg] || $arg < 1 || $arg > $count} {
        puts "Ошибка: укажите номер теста от 1 до $count."
        exit 1
    }
    runTestBackground [lindex $files [expr {$arg - 1}]]
    exit 0
}

# Интерактивный режим
while {1} {
    showMenu $files
    gets stdin choice

    if {![string is integer -strict $choice]} {
        puts "Ошибка: введите число."
        continue
    }

    set choice [expr {$choice}]
    if {$choice == 0} {
        puts "Выход."
        break
    } elseif {$choice >= 1 && $choice <= $count} {
        runTestBackground [lindex $files [expr {$choice - 1}]]
        # После завершения теста — возвращаемся к меню
    } else {
        puts "Ошибка: выберите число от 1 до $count или 0."
    }
}

